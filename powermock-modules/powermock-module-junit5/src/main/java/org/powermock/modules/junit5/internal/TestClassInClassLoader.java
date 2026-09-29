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
        // @Nested classes share the MockClassLoader of their outermost test class (inheriting its @PrepareForTest)
        ExtensionContext outermost = context;
        for (ExtensionContext c = context; c != null && c.getTestClass().isPresent(); c = c.getParent().orElse(null)) {
            outermost = c;
        }
        Class<?> originalTestClass = outermost.getRequiredTestClass();
        return outermost.getStore(NAMESPACE).getOrComputeIfAbsent(
            originalTestClass, TestClassInClassLoader::new, TestClassInClassLoader.class);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public Class<?> getTestClass() {
        return testClass;
    }

    public void createShadowInstance(Object originalInstance, ExtensionContext context) throws Exception {
        final Class<?> testClass = loadedClass(originalInstance.getClass());
        Object shadow;
        Constructor<?> constructor = null;
        try {
            constructor = testClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            // constructor with Jupiter-resolved parameters (or an inner @Nested class): mirror the original's state
        }
        if (constructor != null) {
            constructor.setAccessible(true);
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, constructor::newInstance);
        } else {
            final Object original = originalInstance;
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
                Object copy = new org.objenesis.ObjenesisStd(true).newInstance(testClass);
                CrossLoaderConverter converter = new CrossLoaderConverter(classLoader).seed(original, copy);
                for (java.lang.reflect.Field f : original.getClass().getDeclaredFields()) {
                    if (!f.isSynthetic() || !f.getName().startsWith("this$")) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object enclosing = f.get(original);
                    Object enclosingShadow = context.getStore(NAMESPACE).get(enclosing);
                    if (enclosingShadow != null) {
                        converter.seed(enclosing, enclosingShadow);
                    }
                }
                converter.copyFields(original, copy);
                return copy;
            });
        }
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
    }

    private Class<?> loadedClass(Class<?> original) {
        try {
            return Class.forName(original.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + original + " in PowerMock's MockClassLoader", e);
        }
    }

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        Object shadow = context.getStore(NAMESPACE).get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
