package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Redirects a Jupiter method invocation (test or lifecycle method) to the same method of the test class loaded
 * by the MockClassLoader, with that loader as context class loader. Jupiter's own invocation is skipped.
 */
public final class MockedInvoker {

    private MockedInvoker() {
    }

    public static <T> T invoke(Invocation<T> invocation, ReflectiveInvocationContext<Method> invocationContext,
                               ExtensionContext extensionContext) throws Throwable {
        invocation.skip();
        final Class<?> mockedClass = MockClassLoaderCache.mockedTestClass(extensionContext);
        final ClassLoader mockLoader = mockedClass.getClassLoader();
        final Method original = invocationContext.getExecutable();
        final Method method = mockedMethod(mockedClass, original);
        final Object target = Modifier.isStatic(method.getModifiers()) ? null
                : MockedTestInstances.get(extensionContext, invocationContext.getTarget().orElse(null), mockedClass);

        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(mockLoader);
        try {
            @SuppressWarnings("unchecked")
            final T result = (T) method.invoke(target, invocationContext.getArguments().toArray());
            return result;
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static Method mockedMethod(Class<?> mockedClass, Method original) throws Exception {
        final ClassLoader mockLoader = mockedClass.getClassLoader();
        final Class<?>[] originalTypes = original.getParameterTypes();
        final Class<?>[] types = new Class<?>[originalTypes.length];
        for (int i = 0; i < originalTypes.length; i++) {
            types[i] = originalTypes[i].isPrimitive() ? originalTypes[i]
                    : Class.forName(originalTypes[i].getName(), false, mockLoader);
        }
        // the method may be declared in a superclass or (default method) in an interface of the test class
        final Class<?> declaring = Class.forName(original.getDeclaringClass().getName(), false, mockLoader);
        final Method method = declaring.getDeclaredMethod(original.getName(), types);
        method.setAccessible(true);
        return method;
    }
}
