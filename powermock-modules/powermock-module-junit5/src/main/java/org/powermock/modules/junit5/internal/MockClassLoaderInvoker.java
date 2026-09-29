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
        invokeWithResult(loaded, ic, context);
    }

    public static Object invokeWithResult(TestClassInClassLoader loaded, ReflectiveInvocationContext<Method> ic,
                                          ExtensionContext context) throws Throwable {
        final Method method = findMethod(loaded.getClassLoader(), ic.getExecutable());
        final Object target = ic.getTarget().isPresent()
            ? loaded.getShadowInstance(ic.getTarget().get(), context)
            : null;
        final Object[] args = ic.getArguments().toArray();
        for (int i = 0; i < args.length; i++) {
            args[i] = convertArgument(args[i], loaded.getClassLoader());
        }
        final Object original = ic.getTarget().orElse(null);
        FieldSync.copy(original, target);
        try {
            return withContextClassLoader(loaded.getClassLoader(), () -> method.invoke(target, args));
        } catch (InvocationTargetException e) {
            throw translate(e.getCause(), ic.getExecutable().getDeclaringClass().getClassLoader());
        } finally {
            FieldSync.copy(target, original);
        }
    }

    /**
     * Translates arguments created in the system class loader (e.g. by Jupiter argument sources)
     * into their counterparts in the MockClassLoader: enum constants and Class literals.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convertArgument(Object arg, ClassLoader classLoader) throws ClassNotFoundException {
        if (arg instanceof Class) {
            Class<?> c = (Class<?>) arg;
            if (c.isPrimitive() || c.isArray() || c.getClassLoader() == null) {
                return c;
            }
            return Class.forName(c.getName(), false, classLoader);
        }
        if (arg instanceof Enum) {
            Class<?> enumClass = ((Enum<?>) arg).getDeclaringClass();
            if (enumClass.getClassLoader() == null) {
                return arg;
            }
            Class<?> target = Class.forName(enumClass.getName(), false, classLoader);
            if (target == enumClass) {
                return arg;
            }
            return Enum.valueOf((Class) target, ((Enum<?>) arg).name());
        }
        if (arg != null && arg.getClass().getClassLoader() != null && !arg.getClass().isArray()) {
            Class<?> target;
            try {
                target = Class.forName(arg.getClass().getName(), false, classLoader);
            } catch (ClassNotFoundException e) {
                return arg;
            }
            if (target == arg.getClass()) {
                return arg;
            }
            // copy a user-class object (e.g. from a Jupiter argument source) into the MockClassLoader
            Object copy = org.powermock.reflect.Whitebox.newInstance(target);
            for (Class<?> c = arg.getClass(), t = target; c != null && c != Object.class; c = c.getSuperclass(), t = t.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        java.lang.reflect.Field tf = t.getDeclaredField(f.getName());
                        tf.setAccessible(true);
                        tf.set(copy, convertArgument(f.get(arg), classLoader));
                    } catch (ReflectiveOperationException e) {
                        return arg;
                    }
                }
            }
            return copy;
        }
        return arg;
    }

    /**
     * Translates an exception whose class was loaded by the MockClassLoader into the same exception type of the
     * test's own class loader, so that Jupiter, extensions and watchers see the user's type. Keeps message,
     * stack trace, cause and suppressed exceptions; returns the original if no translation is possible.
     */
    static Throwable translate(Throwable t, ClassLoader targetLoader) {
        return translate(t, targetLoader, 0);
    }

    private static Throwable translate(Throwable t, ClassLoader targetLoader, int depth) {
        if (t == null || targetLoader == null || depth > 10) {
            return t;
        }
        Class<?> type = t.getClass();
        Throwable result = t;
        if (type.getClassLoader() != null && type.getClassLoader() != targetLoader) {
            try {
                Class<?> targetType = Class.forName(type.getName(), false, targetLoader);
                if (targetType != type && Throwable.class.isAssignableFrom(targetType)) {
                    result = instantiate(targetType, t.getMessage());
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // type not visible in the test's loader: keep the original
            }
        }
        if (result == null) {
            return t;
        }
        if (result != t) {
            result.setStackTrace(t.getStackTrace());
            Throwable cause = t.getCause();
            if (cause != null && cause != t) {
                try {
                    result.initCause(translate(cause, targetLoader, depth + 1));
                } catch (IllegalStateException | IllegalArgumentException ignored) {
                    // cause already set by the constructor
                }
            }
            for (Throwable suppressed : t.getSuppressed()) {
                result.addSuppressed(translate(suppressed, targetLoader, depth + 1));
            }
        }
        return result;
    }

    private static Throwable instantiate(Class<?> type, String message) {
        try {
            java.lang.reflect.Constructor<?> c = type.getDeclaredConstructor(String.class);
            c.setAccessible(true);
            return (Throwable) c.newInstance(message);
        } catch (ReflectiveOperationException | RuntimeException e) {
            try {
                Throwable created = (Throwable) org.powermock.reflect.Whitebox.newInstance(type);
                org.powermock.reflect.Whitebox.setInternalState(created, "detailMessage", message, Throwable.class);
                return created;
            } catch (RuntimeException e2) {
                return null;
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
