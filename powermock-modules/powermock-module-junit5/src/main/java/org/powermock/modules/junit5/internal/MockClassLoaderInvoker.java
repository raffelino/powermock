package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstances;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Redirects a Jupiter method invocation (test, template, factory or lifecycle method) to the same method of the
 * MockClassLoader-loaded test class / shadow instance, with the MockClassLoader as context class loader:
 * arguments are transferred into the MockClassLoader, instance fields are synchronised both ways, and exceptions
 * are translated back to the application class loader's types.
 */
public class MockClassLoaderInvoker {

    public static Object invoke(TestClassInClassLoader loaded, ReflectiveInvocationContext<Method> ic,
                                ExtensionContext context) throws Throwable {
        Method original = ic.getExecutable();
        final Method method = findMethod(loaded.getClassLoader(), original);
        List<Object> instances = ic.getTarget().isPresent() ? instances(context, ic.getTarget().get())
            : Collections.emptyList();
        for (Object instance : instances) {
            loaded.fieldSync().toShadow(instance);
        }
        final Object target = ic.getTarget().isPresent() ? loaded.getShadowInstance(ic.getTarget().get()) : null;
        List<Object> arguments = ic.getArguments();
        final Object[] args = new Object[arguments.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = loaded.toMockClassLoader(arguments.get(i));
        }
        ClassLoader applicationLoader = original.getDeclaringClass().getClassLoader();
        try {
            return withContextClassLoader(loaded.getClassLoader(), () -> method.invoke(target, args));
        } catch (InvocationTargetException e) {
            throw loaded.toApplication(e.getCause(), applicationLoader);
        } finally {
            for (Object instance : instances) {
                loaded.fieldSync().toOriginal(instance);
            }
        }
    }

    private static List<Object> instances(ExtensionContext context, Object target) {
        TestInstances testInstances = context.getTestInstances().orElse(null);
        if (testInstances != null && testInstances.getAllInstances().contains(target)) {
            return testInstances.getAllInstances();
        }
        return Collections.singletonList(target);
    }

    static Method findMethod(ClassLoader classLoader, Method original) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> declaringClass = Class.forName(original.getDeclaringClass().getName(), false, classLoader);
        Method method = declaringClass.getDeclaredMethod(original.getName(), mapTypes(classLoader, original.getParameterTypes()));
        method.setAccessible(true);
        return method;
    }

    static Class<?>[] mapTypes(ClassLoader classLoader, Class<?>[] originalTypes) throws ClassNotFoundException {
        Class<?>[] types = new Class<?>[originalTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = originalTypes[i].isPrimitive() ? originalTypes[i]
                : Class.forName(originalTypes[i].getName(), false, classLoader);
        }
        return types;
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
