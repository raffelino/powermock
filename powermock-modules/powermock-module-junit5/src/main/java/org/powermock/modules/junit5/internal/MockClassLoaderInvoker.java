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

    public static Object invoke(TestClassInClassLoader loaded, ReflectiveInvocationContext<Method> ic,
                              ExtensionContext context) throws Throwable {
        final Method method = findMethod(loaded.getClassLoader(), ic.getExecutable());
        final Object target = ic.getTarget().isPresent()
            ? loaded.getShadowInstance(ic.getTarget().get(), context)
            : null;
        final Object[] args = ArgumentConverter.convert(loaded.getClassLoader(), ic.getArguments().toArray());
        final Object original = ic.getTarget().orElse(null);
        InstanceFieldSync.copyWithEnclosing(original, target);
        if (target != null) {
            ArgumentConverter.copyInjectedFields(original, target, loaded.getClassLoader());
        }
        try {
            return withContextClassLoader(loaded.getClassLoader(), () -> method.invoke(target, args));
        } catch (InvocationTargetException e) {
            throw toTestClassLoader(e.getCause(), original != null ? original.getClass().getClassLoader()
                : ic.getExecutable().getDeclaringClass().getClassLoader());
        } finally {
            InstanceFieldSync.copyWithEnclosing(target, original);
        }
    }

    /**
     * Re-creates a throwable thrown by MockClassLoader-loaded code with the classes of the given (Jupiter side)
     * class loader, so that user exception types are seen as themselves by handlers, watchers and the engine.
     * Falls back to the original throwable if it cannot be converted.
     */
    static Throwable toTestClassLoader(Throwable t, final ClassLoader target) {
        if (t == null || target == null || t.getClass().getClassLoader() == target
            || t.getClass().getClassLoader() == null) {
            return t;
        }
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes);
            out.writeObject(t);
            out.close();
            java.io.ObjectInputStream in = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes.toByteArray())) {
                @Override
                protected Class<?> resolveClass(java.io.ObjectStreamClass desc)
                    throws java.io.IOException, ClassNotFoundException {
                    try {
                        return Class.forName(desc.getName(), false, target);
                    } catch (ClassNotFoundException e) {
                        return super.resolveClass(desc);
                    }
                }
            };
            Object converted = in.readObject();
            return converted instanceof Throwable ? (Throwable) converted : t;
        } catch (Exception | LinkageError e) {
            return t;
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
