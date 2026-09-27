package org.powermock.modules.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.powermock.modules.junit5.internal.MockClassLoaderCache;
import org.powermock.modules.junit5.internal.MockRepositoryCleaner;
import org.powermock.modules.junit5.internal.MockedInvoker;

import java.lang.reflect.Method;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter requires the test instance to be an instance of the (system class loader) test class, so a
 * {@code TestInstanceFactory} cannot hand out the MockClassLoader-loaded instance. Instead Jupiter creates its
 * normal instance and every test/lifecycle method invocation is redirected (see {@link MockedInvoker}) to the
 * same method of a companion instance of the test class loaded by the MockClassLoader built from the test
 * class's annotations (see {@link MockClassLoaderCache}).
 */
public class PowerMockExtension implements InvocationInterceptor, AfterEachCallback {

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
        MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // runs after all @AfterEach methods, like the JUnit 4 runner's clean-up after each test
        MockRepositoryCleaner.clear(MockClassLoaderCache.classLoaderFor(context));
    }
}
