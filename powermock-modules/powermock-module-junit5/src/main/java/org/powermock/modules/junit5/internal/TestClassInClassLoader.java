package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;

import java.lang.reflect.Constructor;

/**
 * One MockClassLoader per test class (built from @PrepareForTest, @PowerMockIgnore,
 * @SuppressStaticInitializationFor, mock policies ... exactly as the TestNG module does), the test
 * class loaded by it, and the shadow instances mirroring Jupiter's test instances.
 * Cached in the test class's ExtensionContext store, so it lives as long as the test class runs.
 */
public class TestClassInClassLoader {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestClassInClassLoader.class);

    private final ClassLoader classLoader;
    private final Class<?> testClass;

    private TestClassInClassLoader(Class<?> originalTestClass) {
        String[] packagesToIgnore = new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(originalTestClass);
        this.classLoader = new MockClassLoaderFactory(originalTestClass, packagesToIgnore).createForClass(null);
        // @Nested classes share this loader, so their own @PrepareForTest entries must be covered too.
        if (classLoader instanceof org.powermock.core.classloader.MockClassLoader) {
            addNestedPrepareForTest(originalTestClass,
                ((org.powermock.core.classloader.MockClassLoader) classLoader).getConfiguration());
        }
        new MockPolicyInitializerImpl(originalTestClass).initialize(classLoader);
        try {
            this.testClass = Class.forName(originalTestClass.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + originalTestClass + " in PowerMock's MockClassLoader", e);
        }
    }

    private static void addNestedPrepareForTest(Class<?> outer, org.powermock.core.classloader.MockClassLoaderConfiguration configuration) {
        for (Class<?> nested : outer.getDeclaredClasses()) {
            if (!java.lang.reflect.Modifier.isStatic(nested.getModifiers())) {
                configuration.addClassesToModify(new org.powermock.tests.utils.impl.PrepareForTestExtractorImpl().getTestClasses(nested));
                addNestedPrepareForTest(nested, configuration);
            }
        }
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        Class<?> originalTestClass = context.getRequiredTestClass();
        // @Nested classes share the MockClassLoader of their outermost class, so that the nested
        // shadow instance can be created with the outer shadow instance as enclosing instance.
        while (originalTestClass.getEnclosingClass() != null && !java.lang.reflect.Modifier.isStatic(originalTestClass.getModifiers())) {
            originalTestClass = originalTestClass.getEnclosingClass();
        }
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(
            originalTestClass, TestClassInClassLoader::new, TestClassInClassLoader.class);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public Class<?> getTestClass() {
        return testClass;
    }

    public void createShadowInstance(Object originalInstance, ExtensionContext context) throws Exception {
        Class<?> originalClass = originalInstance.getClass();
        Class<?> shadowClass = Class.forName(originalClass.getName(), false, classLoader);
        Object created;
        if (originalClass.getEnclosingClass() != null && !java.lang.reflect.Modifier.isStatic(originalClass.getModifiers())) {
            Object outer = enclosingInstance(originalInstance);
            Object outerShadow = getShadowInstance(outer, context);
            Constructor<?> constructor = shadowClass.getDeclaredConstructor(outerShadow.getClass());
            constructor.setAccessible(true);
            created = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(outerShadow));
        } else {
            try {
                Constructor<?> constructor = shadowClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                created = MockClassLoaderInvoker.withContextClassLoader(classLoader, constructor::newInstance);
            } catch (NoSuchMethodException e) {
                // constructor injected by Jupiter: copy the original instance's state into the MockClassLoader
                created = MockClassLoaderInvoker.withContextClassLoader(classLoader,
                    () -> MockClassLoaderInvoker.convertArgument(originalInstance, classLoader));
            }
        }
        final Object shadow = created;
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
        context.getRoot().getStore(NAMESPACE).put(originalInstance, shadow);
        MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
            injectMockAnnotations(shadow);
            return null;
        });
    }

    /**
     * Processes mock annotations (@Mock, @Spy, @Captor, @InjectMocks; EasyMock @Mock/@MockNice/@MockStrict)
     * on the shadow instance, like the JUnit 4 runner does, using the AnnotationEnabler of whichever
     * PowerMock API is on the class path, loaded by the MockClassLoader.
     */
    /**
     * The Mockito and EasyMock APIs ship an AnnotationEnabler under the same class name, so with both on
     * the class path only one of them is found above; EasyMock's annotations are therefore injected here.
     */
    @SuppressWarnings("unchecked")
    public void injectEasyMockAnnotations(Object originalInstance, ExtensionContext context) throws Exception {
        Object shadow = getShadowInstance(originalInstance, context);
        MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
            injectEasyMockAnnotations(shadow);
            return null;
        });
    }

    private void injectEasyMockAnnotations(Object shadow) throws Exception {
        Class<?> powerMock;
        try {
            powerMock = Class.forName("org.powermock.api.easymock.PowerMock", true, classLoader);
        } catch (ClassNotFoundException e) {
            return;
        }
        String[][] kinds = {
            {"org.powermock.api.easymock.annotation.Mock", "createMock"},
            {"org.powermock.api.easymock.annotation.MockNice", "createNiceMock"},
            {"org.powermock.api.easymock.annotation.MockStrict", "createStrictMock"},
        };
        for (Class<?> c = shadow.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                for (String[] kind : kinds) {
                    Class<? extends java.lang.annotation.Annotation> annotation =
                        (Class<? extends java.lang.annotation.Annotation>) Class.forName(kind[0], true, classLoader);
                    if (field.isAnnotationPresent(annotation) && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        {
                            // overwrite: the Mockito AnnotationEnabler may already have put a Mockito mock here
                            Object mock = powerMock.getMethod(kind[1], Class.class)
                                .invoke(null, field.getType());
                            field.set(shadow, mock);
                        }
                    }
                }
            }
        }
    }

    private void injectMockAnnotations(Object shadow) throws Exception {
        Class<?> enablerClass;
        try {
            enablerClass = Class.forName("org.powermock.api.extension.listener.AnnotationEnabler", true, classLoader);
        } catch (ClassNotFoundException e) {
            return;
        }
        Object enabler = enablerClass.getDeclaredConstructor().newInstance();
        enablerClass.getMethod("beforeTestMethod", Object.class, java.lang.reflect.Method.class, Object[].class)
                .invoke(enabler, shadow, null, new Object[0]);
    }

    private static Object enclosingInstance(Object inner) throws IllegalAccessException {
        for (java.lang.reflect.Field field : inner.getClass().getDeclaredFields()) {
            if (field.isSynthetic() && field.getName().startsWith("this$")) {
                field.setAccessible(true);
                return field.get(inner);
            }
        }
        throw new IllegalStateException("No enclosing instance found for " + inner);
    }

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        Object shadow = context.getStore(NAMESPACE).get(originalInstance);
        if (shadow == null) {
            shadow = context.getRoot().getStore(NAMESPACE).get(originalInstance);
        }
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
