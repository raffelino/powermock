package org.powermock.modules.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.powermock.core.MockRepository;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.reflect.Whitebox;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter requires the test instance to be an instance of the test class as loaded by Jupiter, so a
 * {@code TestInstanceFactory} cannot hand back the MockClassLoader copy. Instead Jupiter keeps its own
 * (unused) instance and every test/lifecycle method invocation is skipped and redirected to the method of the
 * same name on the MockClassLoader copy of the class (static methods) or on a copy instance created per test.
 */
public class PowerMockExtension implements InvocationInterceptor, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(PowerMockExtension.class);

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        redirect(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
        redirect(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        redirect(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                         ExtensionContext extensionContext) throws Throwable {
        redirect(invocation, invocationContext, extensionContext);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        redirect(invocation, invocationContext, extensionContext);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // MockRepository state lives in the MockClassLoader copy, so clear that one.
        Whitebox.invokeMethod(mockClass(context).getClassLoader().loadClass(MockRepository.class.getName()), "clear");
    }

    private void redirect(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                          ExtensionContext context) throws Throwable {
        invocation.skip();
        Method method = invocationContext.getExecutable();
        Class<?> mockClass = mockClass(context);
        Class<?> declaring = Class.forName(method.getDeclaringClass().getName(), false, mockClass.getClassLoader());
        Method target = declaring.getDeclaredMethod(method.getName(), method.getParameterTypes());
        target.setAccessible(true);
        Object instance = Modifier.isStatic(method.getModifiers()) ? null : mockInstance(context, mockClass);

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(mockClass.getClassLoader());
        try {
            target.invoke(instance, invocationContext.getArguments().toArray());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /** The test class loaded by a MockClassLoader built from its annotations; one per test class. */
    private static Class<?> mockClass(ExtensionContext context) {
        final Class<?> testClass = context.getRequiredTestClass();
        ExtensionContext classContext = context;
        while (classContext.getTestMethod().isPresent()) {
            classContext = classContext.getParent().get();
        }
        return (Class<?>) classContext.getStore(NAMESPACE)
                .getOrComputeIfAbsent(testClass, PowerMockExtension::loadInMockClassLoader);
    }

    private static Class<?> loadInMockClassLoader(Class<?> testClass) {
        String[] packagesToIgnore = new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(testClass);
        ClassLoader mockLoader = new MockClassLoaderFactory(testClass, packagesToIgnore).createForClass(null);
        new MockPolicyInitializerImpl(testClass).initialize(mockLoader);
        try {
            return Class.forName(testClass.getName(), false, mockLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + testClass + " in PowerMock's MockClassLoader", e);
        }
    }

    /** One instance of the MockClassLoader copy per test method (lives in the method-level store). */
    private static Object mockInstance(ExtensionContext context, final Class<?> mockClass) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent("instance", new java.util.function.Function<String, Object>() {
            @Override
            public Object apply(String key) {
                try {
                    java.lang.reflect.Constructor<?> constructor = mockClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    return constructor.newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot instantiate " + mockClass.getName() + " in PowerMock's MockClassLoader", e);
                }
            }
        });
    }
}
