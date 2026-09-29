package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * One MockClassLoader per test class (built from @PrepareForTest, @PowerMockIgnore,
 * @SuppressStaticInitializationFor, mock policies ... exactly as the TestNG module does), the test
 * class loaded by it, and the shadow instances mirroring Jupiter's test instances.
 * Cached in the test class's ExtensionContext store, so it lives as long as the test class runs.
 */
public class TestClassInClassLoader {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestClassInClassLoader.class);

    private static final String ANNOTATION_ENABLER = "org.powermock.api.extension.listener.AnnotationEnabler";

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
        final Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, constructor::newInstance);
        MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
            injectAnnotatedMocks(shadow);
            return null;
        });
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
    }

    /**
     * Processes the mock annotations of the mocking API on the class path (Mockito's @Mock, @Spy, @Captor,
     * @InjectMocks or EasyMock's @Mock, @MockNice, @MockStrict) on a fresh shadow instance, exactly like
     * the JUnit 4 runner and the TestNG module do via the API's AnnotationEnabler.
     */
    private void injectAnnotatedMocks(Object shadow) throws Exception {
        Class<?> enablerClass;
        try {
            enablerClass = Class.forName(ANNOTATION_ENABLER, true, classLoader);
        } catch (ClassNotFoundException e) {
            return; // no PowerMock mocking API with annotation support on the class path
        }
        Object enabler = enablerClass.getDeclaredConstructor().newInstance();
        Method beforeTestMethod = enablerClass.getMethod("beforeTestMethod", Object.class, Method.class, Object[].class);
        try {
            beforeTestMethod.invoke(enabler, shadow, null, new Object[0]);
        } catch (InvocationTargetException e) {
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
