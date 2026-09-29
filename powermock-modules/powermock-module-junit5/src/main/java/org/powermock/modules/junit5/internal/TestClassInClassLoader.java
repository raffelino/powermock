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
        while (!context.getTestClass().isPresent() && context.getParent().isPresent()) {
            context = context.getParent().get(); // e.g. dynamic tests
        }
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
        Object shadow;
        Constructor<?> constructor;
        try {
            constructor = testClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            constructor = null;
        }
        if (constructor != null) {
            constructor.setAccessible(true);
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, constructor::newInstance);
            // fields set by Jupiter extensions before us (e.g. @TempDir) are mirrored
            ClassLoaderBridge.copyAnnotatedFields(originalInstance, shadow, classLoader);
        } else {
            // constructor parameters were resolved by Jupiter for the original instance: mirror its state
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader,
                () -> ClassLoaderBridge.allocate(testClass));
            ClassLoaderBridge.copyFields(originalInstance, shadow, classLoader);
        }
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
    }

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        Object shadow = context.getStore(NAMESPACE).get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
