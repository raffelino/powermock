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
        // ponytail: no-arg constructor only (no constructor parameter resolution), add when a test needs it
        Class<?> originalClass = originalInstance.getClass();
        Class<?> shadowClass = Class.forName(originalClass.getName(), false, classLoader);
        final Object[] args;
        final Constructor<?> constructor;
        if (originalClass.getEnclosingClass() != null && !java.lang.reflect.Modifier.isStatic(originalClass.getModifiers())) {
            Object outer = enclosingInstance(originalInstance);
            Object outerShadow = getShadowInstance(outer, context);
            constructor = shadowClass.getDeclaredConstructor(outerShadow.getClass());
            args = new Object[]{outerShadow};
        } else {
            constructor = shadowClass.getDeclaredConstructor();
            args = new Object[0];
        }
        constructor.setAccessible(true);
        Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(args));
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, shadow);
        context.getRoot().getStore(NAMESPACE).put(originalInstance, shadow);
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
