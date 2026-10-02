package org.powermock.modules.junit5;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.powermock.modules.junit5.internal.MockClassLoaderInvoker;
import org.powermock.modules.junit5.internal.TestClassInClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter requires the test instance to be an instance of the original test class, so we cannot
 * hand Jupiter a MockClassLoader-loaded instance. Instead, Jupiter keeps its normal instance and
 * for every one of its instances (created with Jupiter's resolved constructor arguments, @Nested
 * instances inside their enclosing shadow) we create a "shadow" instance of the same class loaded by
 * the MockClassLoader. Every test, template, factory and lifecycle method invocation is redirected to
 * the shadow; arguments are transferred into the MockClassLoader, instance fields are synchronised
 * both ways (so other extensions and the test code see each other's writes), and exceptions are
 * translated back to the application class loader's types. Mock annotations (Mockito and EasyMock
 * API) are processed on the shadow. One MockClassLoader serves a top-level class and its @Nested classes.
 */
public class PowerMockExtension implements InvocationInterceptor, AfterEachCallback {

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation, ReflectiveInvocationContext<Constructor<T>> ic,
                                               ExtensionContext context) throws Throwable {
        T original = invocation.proceed();
        TestClassInClassLoader.of(context).createShadowInstance(original, ic.getExecutable(), ic.getArguments().toArray());
        return original;
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
        if (context.getTestInstanceLifecycle().orElse(null) == TestInstance.Lifecycle.PER_CLASS) {
            TestClassInClassLoader.of(context).retainInstanceMocksAfterBeforeAll();
        }
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                          ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                    ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                            ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation, ReflectiveInvocationContext<Method> ic,
                                            ExtensionContext context) throws Throwable {
        return (T) redirect(invocation, ic, context);
    }

    @Override
    public void interceptDynamicTest(final Invocation<Void> invocation, DynamicTestInvocationContext ic,
                                     ExtensionContext context) throws Throwable {
        TestClassInClassLoader loaded = TestClassInClassLoader.of(context);
        try {
            MockClassLoaderInvoker.withContextClassLoader(loaded.getClassLoader(), () -> {
                try {
                    return invocation.proceed();
                } catch (Throwable t) {
                    throw new Wrapped(t);
                }
            });
        } catch (Wrapped w) {
            throw loaded.translate(w.getCause(), PowerMockExtension.class.getClassLoader());
        }
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                        ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestClassInClassLoader.of(context).clearState();
    }

    private static Object redirect(Invocation<?> invocation, ReflectiveInvocationContext<Method> ic,
                                   ExtensionContext context) throws Throwable {
        invocation.skip();
        return MockClassLoaderInvoker.invoke(TestClassInClassLoader.of(context), ic, context);
    }

    private static final class Wrapped extends RuntimeException {
        Wrapped(Throwable cause) {
            super(cause);
        }
    }
}
