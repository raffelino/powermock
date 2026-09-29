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
        // @Nested: configuration (e.g. @PrepareForTest) comes from the outermost class.
        // ponytail: nested classes' own @PrepareForTest are not merged yet
        Class<?> configClass = originalTestClass;
        while (configClass.getEnclosingClass() != null && enclosingInstanceField(configClass) != null) {
            configClass = configClass.getEnclosingClass();
        }
        String[] packagesToIgnore = new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(configClass);
        this.classLoader = new MockClassLoaderFactory(configClass, packagesToIgnore).createForClass(null);
        new MockPolicyInitializerImpl(configClass).initialize(classLoader);
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
        createShadow(originalInstance, context);
    }

    /**
     * Creates the shadow of {@code originalInstance} in this class loader. For a @Nested (inner) class the
     * enclosing instances are shadowed in the same class loader first, so the whole chain shares one loader.
     */
    private Object createShadow(Object originalInstance, ExtensionContext context) throws Exception {
        Class<?> originalClass = originalInstance.getClass();
        final Class<?> shadowClass = Class.forName(originalClass.getName(), false, classLoader);
        Object shadow;
        java.lang.reflect.Field outerField = enclosingInstanceField(originalClass);
        if (outerField == null) {
            // Top-level/static class: constructor-injected (C3) test classes are instantiated with placeholder
            // arguments and the original's field values are copied over.
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader,
                () -> ArgumentConverter.instantiate(shadowClass, originalInstance, classLoader));
        } else {
            outerField.setAccessible(true);
            final Object outerShadow = createShadow(outerField.get(originalInstance), context);
            final Constructor<?> constructor = shadowClass.getDeclaredConstructor(outerShadow.getClass());
            constructor.setAccessible(true);
            shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(outerShadow));
        }
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
        return shadow;
    }

    private static java.lang.reflect.Field enclosingInstanceField(Class<?> clazz) {
        if (clazz.getEnclosingClass() == null || java.lang.reflect.Modifier.isStatic(clazz.getModifiers())) {
            return null;
        }
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            if (f.isSynthetic() && f.getType() == clazz.getEnclosingClass()) {
                return f;
            }
        }
        return null;
    }

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        Object shadow = context.getStore(NAMESPACE).get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
