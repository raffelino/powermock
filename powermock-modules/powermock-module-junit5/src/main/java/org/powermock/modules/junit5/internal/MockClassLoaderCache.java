package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;

/**
 * Creates one MockClassLoader per test class (from @PrepareForTest, @PowerMockIgnore, @MockPolicy, ...) and
 * caches it, together with the test class loaded by it, in the class-level extension store.
 */
public final class MockClassLoaderCache {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(MockClassLoaderCache.class);

    private MockClassLoaderCache() {
    }

    public static ClassLoader classLoaderFor(ExtensionContext context) {
        return mockedTestClass(context).getClassLoader();
    }

    /** The test class as loaded by the MockClassLoader of the (innermost) test class of {@code context}. */
    public static Class<?> mockedTestClass(ExtensionContext context) {
        final Class<?> testClass = context.getRequiredTestClass();
        return classStore(context).getOrComputeIfAbsent(testClass, MockClassLoaderCache::loadInMockClassLoader, Class.class);
    }

    /** Store of the class-level context, so cached values live as long as the test class runs. */
    public static ExtensionContext.Store classStore(ExtensionContext context) {
        ExtensionContext classContext = context;
        while (classContext.getTestMethod().isPresent() && classContext.getParent().isPresent()) {
            classContext = classContext.getParent().get();
        }
        return classContext.getStore(NAMESPACE);
    }

    private static Class<?> loadInMockClassLoader(Class<?> testClass) {
        final String[] packagesToIgnore = new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(testClass);
        final ClassLoader mockLoader = new MockClassLoaderFactory(testClass, packagesToIgnore).createForClass(null);
        new MockPolicyInitializerImpl(testClass).initialize(mockLoader);
        try {
            return Class.forName(testClass.getName(), false, mockLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + testClass + " in PowerMock's MockClassLoader", e);
        }
    }
}
