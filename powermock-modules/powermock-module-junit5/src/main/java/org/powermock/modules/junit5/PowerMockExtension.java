package org.powermock.modules.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.powermock.modules.junit5.internal.MockClassLoaderInvoker;
import org.powermock.modules.junit5.internal.MockitoAnnotationsInMockClassLoader;
import org.powermock.modules.junit5.internal.PowerMockStateCleaner;
import org.powermock.modules.junit5.internal.TestClassInClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter requires the test instance to be an instance of the original test class, so we cannot
 * hand Jupiter a MockClassLoader-loaded instance. Instead, whenever Jupiter constructs a test instance
 * (including {@code @Nested} ones and constructors with resolved parameters) we construct a "shadow" instance
 * of the same class loaded by the MockClassLoader, with the same arguments. Every test and lifecycle method
 * invocation is intercepted and redirected to the corresponding method of the shadow instance (or shadow class
 * for static methods); arguments are transferred into the MockClassLoader and instance fields are synchronised
 * between Jupiter's instances and their shadows around each call, so other extensions keep working on
 * Jupiter's instance. Mockito annotations are processed on the shadows before each test, as PowerMockRunner does.
 * <p>
 * All {@code @Nested} classes of a top-level class share one MockClassLoader preparing the union of their
 * {@code @PrepareForTest} classes.
 */
public class PowerMockExtension implements InvocationInterceptor, BeforeEachCallback, AfterEachCallback {

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation, ReflectiveInvocationContext<Constructor<T>> ic,
                                               ExtensionContext context) throws Throwable {
        T instance = invocation.proceed();
        TestClassInClassLoader.of(context).createShadowInstance(instance, ic.getExecutable(), ic.getArguments());
        return instance;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        TestClassInClassLoader loaded = TestClassInClassLoader.of(context);
        for (Object instance : context.getRequiredTestInstances().getAllInstances()) {
            MockitoAnnotationsInMockClassLoader.process(loaded.getClassLoader(), loaded.getShadow(instance).getShadow());
        }
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                         ExtensionContext context) throws Throwable {
        redirect(invocation, ic, context);
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
        PowerMockStateCleaner.clear(TestClassInClassLoader.of(context).getClassLoader());
    }

    private static void redirect(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                 ExtensionContext context) throws Throwable {
        invocation.skip();
        MockClassLoaderInvoker.invoke(TestClassInClassLoader.of(context), ic, context);
    }
}
