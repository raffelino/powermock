package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Redirects a Jupiter method invocation (test or lifecycle method) to the same method of the
 * MockClassLoader-loaded test class / shadow instance, with the MockClassLoader as context class loader.
 */
public class MockClassLoaderInvoker {

    public static void invoke(TestClassInClassLoader loaded, ReflectiveInvocationContext<Method> ic,
                              ExtensionContext context) throws Throwable {
        final Method method = findMethod(loaded.getClassLoader(), ic.getExecutable());
        final Object target = ic.getTarget().isPresent()
            ? loaded.getShadowInstance(ic.getTarget().get(), context)
            : null;
        final CrossClassLoaderConverter converter = new CrossClassLoaderConverter(loaded.getClassLoader());
        final Object[] args = converter.convertAll(ic.getArguments().toArray());
        final Object original = ic.getTarget().orElse(null);
        if (original != null) {
            copyStateIntoShadow(converter, original, target);
        }
        try {
            withContextClassLoader(loaded.getClassLoader(), () -> method.invoke(target, args));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            if (original != null) {
                copyStateFromShadow(target, original);
            }
        }
    }

    /**
     * Fields set on Jupiter's instance by Jupiter or other extensions (@TempDir, TestInstancePostProcessors ...)
     * are copied into the shadow instance where it has no value of its own.
     */
    private static void copyStateIntoShadow(CrossClassLoaderConverter converter, Object original, Object shadow) throws Exception {
        for (Class<?> c = original.getClass(), s = shadow.getClass(); c != null && c != Object.class;
             c = c.getSuperclass(), s = s.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(original);
                java.lang.reflect.Field shadowField = s.getDeclaredField(field.getName());
                shadowField.setAccessible(true);
                if (value != null && shadowField.get(shadow) == null && !shadowField.getType().isPrimitive()) {
                    Object converted = converter.convert(value);
                    if (shadowField.getType().isInstance(converted)) {
                        shadowField.set(shadow, converted);
                    }
                }
            }
        }
    }

    /** The shadow's field values are mirrored back where the types are compatible, so other extensions see them. */
    private static void copyStateFromShadow(Object shadow, Object original) throws Exception {
        for (Class<?> c = original.getClass(), s = shadow.getClass(); c != null && c != Object.class;
             c = c.getSuperclass(), s = s.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()
                    || java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                java.lang.reflect.Field shadowField = s.getDeclaredField(field.getName());
                shadowField.setAccessible(true);
                Object value = shadowField.get(shadow);
                field.setAccessible(true);
                if (field.getType().isPrimitive() || value == null || field.getType().isInstance(value)) {
                    if (value != null || !field.getType().isPrimitive()) {
                        field.set(original, value);
                    }
                }
            }
        }
    }

    static Method findMethod(ClassLoader classLoader, Method original) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> declaringClass = Class.forName(original.getDeclaringClass().getName(), false, classLoader);
        Class<?>[] originalTypes = original.getParameterTypes();
        Class<?>[] types = new Class<?>[originalTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = originalTypes[i].isPrimitive() ? originalTypes[i]
                : Class.forName(originalTypes[i].getName(), false, classLoader);
        }
        Method method = declaringClass.getDeclaredMethod(original.getName(), types);
        method.setAccessible(true);
        return method;
    }

    public static <T> T withContextClassLoader(ClassLoader classLoader, Callable<T> callable) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return callable.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
