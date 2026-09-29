package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Redirects a Jupiter method invocation (test, template, factory or lifecycle method) to the same method of the
 * MockClassLoader-loaded test class / shadow instance, with the MockClassLoader as context class loader.
 * Arguments are converted into the MockClassLoader, instance fields are synchronised before and after, and an
 * exception leaving the MockClassLoader is converted back to the application class loader's types.
 */
public class MockClassLoaderInvoker {

    public static Object invoke(TestClassInClassLoader loaded, ReflectiveInvocationContext<Method> ic,
                                ExtensionContext context) throws Throwable {
        ClassLoader mockClassLoader = loaded.getClassLoader();
        ClassLoader originalLoader = ic.getExecutable().getDeclaringClass().getClassLoader();
        final Method method = findMethod(mockClassLoader, ic.getExecutable());
        final ShadowInstance instance = ic.getTarget().isPresent()
            ? loaded.getShadowInstance(ic.getTarget().get(), context)
            : null;
        List<Object> arguments = ic.getArguments();
        final Object[] args = new Object[arguments.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = ObjectTransfer.transfer(arguments.get(i), mockClassLoader, TestClassInClassLoader.counterparts(context));
        }
        if (instance != null) {
            instance.toShadow(TestClassInClassLoader.counterparts(context));
        }
        try {
            final Object target = instance == null ? null : instance.getShadow();
            return withContextClassLoader(mockClassLoader, () -> method.invoke(target, args));
        } catch (InvocationTargetException e) {
            throw ObjectTransfer.transferThrowable(e.getCause(), originalLoader);
        } finally {
            if (instance != null) {
                instance.toOriginal(TestClassInClassLoader.reverseCounterparts(instance));
            }
        }
    }

    static Method findMethod(ClassLoader classLoader, Method original) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> declaringClass = Class.forName(original.getDeclaringClass().getName(), false, classLoader);
        Class<?>[] originalTypes = original.getParameterTypes();
        Class<?>[] types = new Class<?>[originalTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = ObjectTransfer.targetClass(originalTypes[i], classLoader);
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
