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
        initMockitoAnnotations(shadow);
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
    }

    /** Processes @Mock/@Spy/@Captor/@InjectMocks on the shadow instance with the MockClassLoader's Mockito, like PowerMockRunner. */
    private void initMockitoAnnotations(final Object shadow) throws Exception {
        final Class<?> enabler;
        try {
            // PowerMock's own annotation processing (as in PowerMockRunner): handles spies of prepared/final classes
            enabler = Class.forName("org.powermock.api.mockito.powermocklistener.AnnotationEnabler", true, classLoader);
        } catch (ClassNotFoundException e) {
            return; // powermock-api-mockito2 not on the test class path
        }
        try {
            MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
                Object listener = enabler.getConstructor().newInstance();
                return enabler.getMethod("beforeTestMethod", Object.class, java.lang.reflect.Method.class, Object[].class)
                    .invoke(listener, shadow, null, new Object[0]);
            });
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
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
