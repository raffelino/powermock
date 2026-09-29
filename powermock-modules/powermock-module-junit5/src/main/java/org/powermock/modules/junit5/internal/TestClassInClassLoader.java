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
        addPreparedClassesOfNestedClasses(originalTestClass);
        new MockPolicyInitializerImpl(originalTestClass).initialize(classLoader);
        try {
            this.testClass = Class.forName(originalTestClass.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + originalTestClass + " in PowerMock's MockClassLoader", e);
        }
    }

    /**
     * @Nested classes share this loader, so their own @PrepareForTest / @SuppressStaticInitializationFor
     * must be known to it before any class is loaded.
     */
    private void addPreparedClassesOfNestedClasses(Class<?> enclosing) {
        if (!(classLoader instanceof org.powermock.core.classloader.MockClassLoader)) {
            return;
        }
        org.powermock.core.classloader.MockClassLoaderConfiguration configuration =
            ((org.powermock.core.classloader.MockClassLoader) classLoader).getConfiguration();
        for (Class<?> nested : enclosing.getDeclaredClasses()) {
            if (java.lang.reflect.Modifier.isStatic(nested.getModifiers())) {
                continue;
            }
            configuration.addClassesToModify(new org.powermock.tests.utils.impl.PrepareForTestExtractorImpl().getTestClasses(nested));
            configuration.addClassesToModify(new org.powermock.tests.utils.impl.StaticConstructorSuppressExtractorImpl().getTestClasses(nested));
            addPreparedClassesOfNestedClasses(nested);
        }
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        while (!context.getTestClass().isPresent() && context.getParent().isPresent()) {
            context = context.getParent().get(); // e.g. dynamic tests
        }
        Class<?> originalTestClass = context.getRequiredTestClass();
        // @Nested classes share the MockClassLoader of their outermost (top-level) test class
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
        java.util.LinkedList<Object> enclosing = new java.util.LinkedList<>();
        for (Object o = outerInstanceOf(originalInstance); o != null; o = outerInstanceOf(o)) {
            enclosing.addFirst(o);
        }
        Object outerShadow = null;
        for (Object outer : enclosing) {
            Object existing = context.getStore(NAMESPACE).get(outer);
            outerShadow = existing != null ? existing : newShadow(outer, outerShadow, context);
        }
        newShadow(originalInstance, outerShadow, context);
    }

    private static Object outerInstanceOf(Object instance) throws IllegalAccessException {
        Class<?> c = instance.getClass();
        if (c.getEnclosingClass() == null || java.lang.reflect.Modifier.isStatic(c.getModifiers())) {
            return null;
        }
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (f.isSynthetic() && f.getType() == c.getEnclosingClass()) {
                f.setAccessible(true);
                return f.get(instance);
            }
        }
        return null;
    }

    private Object newShadow(Object originalInstance, Object outerShadow, ExtensionContext context) throws Exception {
        final Class<?> shadowClass = Class.forName(originalInstance.getClass().getName(), false, classLoader);
        Constructor<?> constructor;
        try {
            constructor = outerShadow == null
                ? shadowClass.getDeclaredConstructor()
                : shadowClass.getDeclaredConstructor(outerShadow.getClass());
        } catch (NoSuchMethodException e) {
            constructor = null;
        }
        Object shadow;
        if (constructor != null) {
            constructor.setAccessible(true);
            final Constructor<?> ctor = constructor;
            final Object[] args = outerShadow == null ? new Object[0] : new Object[]{outerShadow};
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> ctor.newInstance(args));
            // fields set by Jupiter extensions before us (e.g. @TempDir) are mirrored
            ClassLoaderBridge.copyAnnotatedFields(originalInstance, shadow, classLoader);
        } else {
            // constructor parameters were resolved by Jupiter for the original instance: mirror its state
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader,
                () -> ClassLoaderBridge.allocate(shadowClass));
            ClassLoaderBridge.copyFields(originalInstance, shadow, classLoader);
        }
        AnnotationInjector.inject(classLoader, shadow);
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
        return shadow;
    }

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        Object shadow = context.getStore(NAMESPACE).get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
