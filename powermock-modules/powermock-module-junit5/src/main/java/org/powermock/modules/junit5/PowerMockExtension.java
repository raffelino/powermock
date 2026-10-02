package org.powermock.modules.junit5;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.powermock.modules.junit5.internal.MockClassLoaderInvoker;
import org.powermock.modules.junit5.internal.ObjectTransfer;
import org.powermock.modules.junit5.internal.PowerMockStateCleaner;
import org.powermock.modules.junit5.internal.TestClassInClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter requires the test instance to be an instance of the original test class, so we cannot
 * hand Jupiter a MockClassLoader-loaded instance. Instead, Jupiter keeps its normal instance and
 * for every one of its instances we create a "shadow" instance of the same class loaded by the
 * MockClassLoader (with the same, converted, constructor arguments; for @Nested classes the enclosing
 * instance is the enclosing shadow). Every test, template, factory and lifecycle method invocation is
 * redirected to the corresponding method of the shadow instance (or shadow class for static methods);
 * arguments are converted into the MockClassLoader, instance fields are synchronised in both directions,
 * and exceptions are converted back. Mock annotations (Mockito's and PowerMock's EasyMock ones) are processed
 * on every shadow instance when it is created.
 */
public class PowerMockExtension implements InvocationInterceptor, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(PowerMockExtension.class);

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation, ReflectiveInvocationContext<Constructor<T>> ic,
                                               ExtensionContext context) throws Throwable {
        T instance = invocation.proceed();
        TestClassInClassLoader loaded = TestClassInClassLoader.of(context);
        try {
            loaded.createShadowInstance(instance, ic.getExecutable(), ic.getArguments(), context);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw ObjectTransfer.transferThrowable(e.getCause(), loaded.getOriginalClassLoader(context));
        }
        return instance;
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
        if (context.getTestInstanceLifecycle().orElse(null) == TestInstance.Lifecycle.PER_CLASS) {
            context.getStore(NAMESPACE).put(Snapshot.class, new Snapshot(PowerMockStateCleaner.Snapshot.take()));
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
        invocation.skip();
        return (T) MockClassLoaderInvoker.invoke(TestClassInClassLoader.of(context), ic, context);
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext ic,
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
        } catch (Wrapped e) {
            throw ObjectTransfer.transferThrowable(e.getCause(), loaded.getOriginalClassLoader(context));
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
        try {
            PowerMockStateCleaner.clear(TestClassInClassLoader.of(context).getClassLoader());
        } finally {
            Snapshot snapshot = context.getStore(NAMESPACE).get(Snapshot.class, Snapshot.class);
            if (snapshot != null) {
                snapshot.state.restore();
            }
        }
    }

    private static void redirect(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                 ExtensionContext context) throws Throwable {
        invocation.skip();
        MockClassLoaderInvoker.invoke(TestClassInClassLoader.of(context), ic, context);
    }

    /** PowerMock state created by the @BeforeAll methods of a PER_CLASS test class. */
    private static final class Snapshot {
        private final PowerMockStateCleaner.Snapshot state;

        Snapshot(PowerMockStateCleaner.Snapshot state) {
            this.state = state;
        }
    }

    private static final class Wrapped extends RuntimeException {
        Wrapped(Throwable cause) {
            super(cause);
        }
    }
}
