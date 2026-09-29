package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Redirects a Jupiter method invocation (test or lifecycle method) to the same method of the
 * MockClassLoader-loaded test class / shadow instance, with the MockClassLoader as context class loader.
 * The fields of Jupiter's test instances are synchronised with their shadows before and after the call.
 */
public class MockClassLoaderInvoker {

    public static void invoke(TestClassInClassLoader loaded, ReflectiveInvocationContext<Method> ic,
                              ExtensionContext context) throws Throwable {
        final Method method = (Method) loaded.findExecutable(ic.getExecutable());
        final List<ShadowState> states = instanceStates(loaded, context);
        final Object target = ic.getTarget().isPresent() ? loaded.getShadow(ic.getTarget().get()).getShadow() : null;
        final Object[] args = loaded.getTransfer().toMockClassLoader(ic.getArguments(), method.getParameterTypes());
        for (ShadowState state : states) {
            state.copyToShadow(loaded.getTransfer());
        }
        try {
            withContextClassLoader(loaded.getClassLoader(), () -> method.invoke(target, args));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            for (ShadowState state : states) {
                state.copyToOriginal(loaded.getTransfer());
            }
        }
    }

    /** Shadow states of all Jupiter test instances of the context (enclosing instances first); empty for static calls. */
    static List<ShadowState> instanceStates(TestClassInClassLoader loaded, ExtensionContext context) {
        List<ShadowState> states = new ArrayList<ShadowState>();
        if (context.getTestInstances().isPresent()) {
            for (Object instance : context.getTestInstances().get().getAllInstances()) {
                states.add(loaded.getShadow(instance));
            }
        }
        return states;
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
