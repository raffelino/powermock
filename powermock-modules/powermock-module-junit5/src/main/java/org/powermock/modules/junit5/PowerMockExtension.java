package org.powermock.modules.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
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
    public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        // @RepeatedTest, @ParameterizedTest
        MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        // the returned dynamic tests' executables are then classes of the MockClassLoader as well
        return MockedInvoker.invoke(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext,
                                     ExtensionContext extensionContext) throws Throwable {
        // the executable was created by the redirected @TestFactory, so it already lives in the MockClassLoader;
        // Mockito/PowerMock just need that loader as context class loader
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(invocationContext.getExecutable().getClass().getClassLoader());
        try {
            invocation.proceed();
        } finally {
            thread.setContextClassLoader(previous);
        }
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
