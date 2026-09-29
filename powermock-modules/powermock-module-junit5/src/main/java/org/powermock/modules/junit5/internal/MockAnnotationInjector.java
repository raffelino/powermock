package org.powermock.modules.junit5.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Injects annotated mocks into a MockClassLoader-loaded (shadow) test instance, the way PowerMockRunner's
 * annotation listeners do: Mockito ({@code @Mock}, {@code @Spy}, {@code @Captor}, {@code @InjectMocks}) via
 * the mockito2 API's AnnotationEnabler, and PowerMock's EasyMock annotations via EasyMockAnnotationSupport.
 * Both classes are loaded by the MockClassLoader so the created mocks live in the same world as the test code.
 */
public class MockAnnotationInjector {

    private static final String MOCKITO_ENABLER = "org.powermock.api.extension.listener.AnnotationEnabler";
    private static final String EASYMOCK_SUPPORT = "org.powermock.api.extension.listener.EasyMockAnnotationSupport";
    private static final String[] EASYMOCK_ANNOTATIONS = {
        "org.powermock.api.easymock.annotation.Mock",
        "org.powermock.api.easymock.annotation.MockNice",
        "org.powermock.api.easymock.annotation.MockStrict"};

    public static void inject(final ClassLoader classLoader, final Object shadowInstance) throws Exception {
        MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
            if (usesAnnotation(classLoader, shadowInstance.getClass(), EASYMOCK_ANNOTATIONS)) {
                Class<?> support = load(classLoader, EASYMOCK_SUPPORT);
                if (support != null) {
                    Object instance = support.getConstructor(Object.class).newInstance(shadowInstance);
                    invoke(support.getMethod("injectMocks"), instance);
                }
            }
            Class<?> enablerClass = load(classLoader, MOCKITO_ENABLER);
            if (enablerClass != null && hasMethod(enablerClass, "injectCaptor")) {
                Object enabler = enablerClass.getConstructor().newInstance();
                invoke(enablerClass.getMethod("beforeTestMethod", Object.class, Method.class, Object[].class),
                    enabler, shadowInstance, null, null);
            }
            return null;
        });
    }

    /** Resets the mock fields so the next test (same instance, e.g. PER_CLASS) gets fresh mocks. */
    public static void clear(final ClassLoader classLoader, final Object shadowInstance) throws Exception {
        String[] all = {"org.mockito.Mock", "org.mockito.Spy", "org.mockito.Captor", "org.mockito.InjectMocks",
            EASYMOCK_ANNOTATIONS[0], EASYMOCK_ANNOTATIONS[1], EASYMOCK_ANNOTATIONS[2]};
        for (Class<?> c = shadowInstance.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!field.getType().isPrimitive() && !java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && isAnnotated(classLoader, field, all)) {
                    field.setAccessible(true);
                    field.set(shadowInstance, null);
                }
            }
        }
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesAnnotation(ClassLoader classLoader, Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (isAnnotated(classLoader, field, names)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean isAnnotated(ClassLoader classLoader, Field field, String[] names) {
        for (String name : names) {
            Class<?> annotation = load(classLoader, name);
            if (annotation != null && field.isAnnotationPresent((Class<? extends Annotation>) annotation)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> load(ClassLoader classLoader, String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static void invoke(Method method, Object target, Object... args) throws Exception {
        try {
            method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }
}
