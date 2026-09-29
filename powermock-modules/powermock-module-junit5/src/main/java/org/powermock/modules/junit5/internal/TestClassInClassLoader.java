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
        new MockPolicyInitializerImpl(originalTestClass).initialize(classLoader);
        try {
            this.testClass = Class.forName(originalTestClass.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + originalTestClass + " in PowerMock's MockClassLoader", e);
        }
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        Class<?> originalTestClass = context.getRequiredTestClass();
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
        // ponytail: no-arg constructor only (no constructor parameter resolution), add when a test needs it
        Constructor<?> constructor = testClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, constructor::newInstance);
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
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

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        Object shadow = context.getStore(NAMESPACE).get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
