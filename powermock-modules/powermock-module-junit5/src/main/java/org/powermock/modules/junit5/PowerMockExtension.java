package org.powermock.modules.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.powermock.modules.junit5.internal.MockClassLoaderInvoker;
import org.powermock.modules.junit5.internal.PowerMockStateCleaner;
import org.powermock.modules.junit5.internal.TestClassInClassLoader;

import java.lang.reflect.Method;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter requires the test instance to be an instance of the original test class, so we cannot
 * hand Jupiter a MockClassLoader-loaded instance. Instead, Jupiter keeps its normal instance and
 * for every one of its instances we create a "shadow" instance of the same class loaded by the
 * MockClassLoader. Every test and lifecycle method invocation is intercepted and redirected to the
 * corresponding method of the shadow instance (or shadow class for static methods).
 */
public class PowerMockExtension implements TestInstancePostProcessor, InvocationInterceptor, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        // fields injected by Jupiter after instance post-processing (e.g. @TempDir) are mirrored to the shadow
        TestClassInClassLoader loaded = TestClassInClassLoader.of(context);
        for (Object instance : context.getRequiredTestInstances().getAllInstances()) {
            org.powermock.modules.junit5.internal.ClassLoaderBridge.copyAnnotatedFields(
                instance, loaded.getShadowInstance(instance, context), loaded.getClassLoader());
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        TestClassInClassLoader.of(context).createShadowInstance(testInstance, context);
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
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation, ReflectiveInvocationContext<Method> ic,
                                            ExtensionContext context) throws Throwable {
        invocation.skip();
        @SuppressWarnings("unchecked")
        T result = (T) MockClassLoaderInvoker.invoke(TestClassInClassLoader.of(context), ic, context);
        return result;
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext dic,
                                     ExtensionContext context) throws Throwable {
        MockClassLoaderInvoker.withContextClassLoader(TestClassInClassLoader.of(context).getClassLoader(), () -> {
            try {
                invocation.proceed();
            } catch (Exception | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            return null;
        });
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
        ClassLoader loader = TestClassInClassLoader.of(context).getClassLoader();
        if (context.getTestInstanceLifecycle().orElse(null) == org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS) {
            // PER_CLASS: instance mocks (e.g. spies created in a non-static @BeforeAll) survive the per-test reset
            PowerMockStateCleaner.clearKeepingInstanceMocks(loader);
        } else {
            PowerMockStateCleaner.clear(loader);
        }
    }

    private static void redirect(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ic,
                                 ExtensionContext context) throws Throwable {
        invocation.skip();
        MockClassLoaderInvoker.invoke(TestClassInClassLoader.of(context), ic, context);
    }
}
