package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
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

    /**
     * Both mocking APIs ship an {@code org.powermock.api.extension.listener.AnnotationEnabler}; only the first on
     * the class path is visible under that name, so EasyMock's is also looked up by its (deprecated) alias.
     */
    private static final String[] ANNOTATION_ENABLERS = {
        "org.powermock.api.extension.listener.AnnotationEnabler",
        "org.powermock.api.easymock.powermocklistener.AnnotationEnabler"
    };

    /** Shadow instances by Jupiter's (original) test instance, including enclosing instances of @Nested classes. */
    private final Map<Object, Object> shadows = new IdentityHashMap<>();

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
        while (originalTestClass.getEnclosingClass() != null && !Modifier.isStatic(originalTestClass.getModifiers())) {
            originalTestClass = originalTestClass.getEnclosingClass(); // @Nested: share the outermost class's loader
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
        final Class<?> shadowClass = Class.forName(originalClass.getName(), false, classLoader);
        final Object[] args;
        final Constructor<?> constructor;
        Object enclosingOriginal = enclosingInstanceOf(originalInstance);
        if (enclosingOriginal != null) {
            // @Nested (inner) class: the shadow inner instance must belong to the shadow of the enclosing instance
            Object enclosingShadow = getShadowInstance(enclosingOriginal, context);
            constructor = shadowClass.getDeclaredConstructor(enclosingShadow.getClass());
            args = new Object[]{enclosingShadow};
        } else {
            // ponytail: no-arg constructor only (no constructor parameter resolution), add when a test needs it
            constructor = shadowClass.getDeclaredConstructor();
            args = new Object[0];
        }
        constructor.setAccessible(true);
        final Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(args));
        MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
            injectAnnotatedMocks(shadow);
            return null;
        });
        synchronized (shadows) {
            shadows.put(originalInstance, shadow);
        }
    }

    private static Object enclosingInstanceOf(Object instance) throws IllegalAccessException {
        Class<?> type = instance.getClass();
        if (type.getEnclosingClass() == null || Modifier.isStatic(type.getModifiers())) {
            return null;
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() && field.getType() == type.getEnclosingClass()) {
                field.setAccessible(true);
                return field.get(instance);
            }
        }
        return null;
    }

    /**
     * Processes the mock annotations of the mocking API on the class path (Mockito's @Mock, @Spy, @Captor,
     * @InjectMocks or EasyMock's @Mock, @MockNice, @MockStrict) on a fresh shadow instance, exactly like
     * the JUnit 4 runner and the TestNG module do via the API's AnnotationEnabler.
     */
    private void injectAnnotatedMocks(Object shadow) throws Exception {
        for (String enablerName : ANNOTATION_ENABLERS) {
            Class<?> enablerClass;
            try {
                enablerClass = Class.forName(enablerName, true, classLoader);
            } catch (ClassNotFoundException e) {
                continue; // this mocking API is not on the class path
            }
            invokeEnabler(enablerClass, shadow);
        }
    }

    private static void invokeEnabler(Class<?> enablerClass, Object shadow) throws Exception {
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
        Object shadow;
        synchronized (shadows) {
            shadow = shadows.get(originalInstance);
        }
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }
}
