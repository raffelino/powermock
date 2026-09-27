package org.powermock.modules.junit5;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.powermock.core.MockRepository;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * JUnit Jupiter extension that runs a test class inside PowerMock's MockClassLoader.
 * <p>
 * Jupiter insists that the test instance is an instance of the class it discovered (loaded by the
 * application class loader), so a {@code TestInstanceFactory} cannot hand it a MockClassLoader copy.
 * Instead Jupiter keeps its own (unused) instance, and every lifecycle and test method invocation is
 * intercepted, skipped and replayed on the same method of the test class loaded by a MockClassLoader
 * built from the class-level annotations ({@code @PrepareForTest}, {@code @PowerMockIgnore}, ...),
 * one class loader per test class. {@link MockRepository} is cleared after each test.
 */
public class PowerMockExtension implements BeforeAllCallback, AfterEachCallback, InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(PowerMockExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        mockTestClass(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        MockRepository.clear();
    }

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx, ExtensionContext context) throws Throwable {
        replay(invocation, ctx, context, null);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx, ExtensionContext context) throws Throwable {
        replay(invocation, ctx, context, null);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx, ExtensionContext context) throws Throwable {
        replay(invocation, ctx, context, mockTestInstance(ctx, context));
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx, ExtensionContext context) throws Throwable {
        replay(invocation, ctx, context, mockTestInstance(ctx, context));
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx, ExtensionContext context) throws Throwable {
        replay(invocation, ctx, context, mockTestInstance(ctx, context));
    }

    private static Class<?> mockTestClass(ExtensionContext context) {
        final Class<?> testClass = context.getRequiredTestClass();
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(testClass, k -> loadInMockClassLoader(testClass), Class.class);
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

    /** One MockClassLoader instance per Jupiter test instance, kept in the store of the current (method) context. */
    private static Object mockTestInstance(ReflectiveInvocationContext<Method> ctx, ExtensionContext context) {
        final Object target = ctx.getTarget().orElseThrow(() -> new IllegalStateException("No test instance"));
        final Class<?> mockClass = mockTestClass(context);
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(new InstanceKey(target), k -> newInstance(mockClass));
    }

    private static Object newInstance(Class<?> mockClass) {
        try {
            Constructor<?> constructor = mockClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return withContextClassLoader(mockClass.getClassLoader(), () -> constructor.newInstance());
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot create an instance of " + mockClass + " in PowerMock's MockClassLoader", e);
        }
    }

    private static void replay(Invocation<Void> invocation, ReflectiveInvocationContext<Method> ctx, ExtensionContext context, Object mockInstance) throws Throwable {
        invocation.skip();
        final Method original = ctx.getExecutable();
        final Class<?> mockClass = mockTestClass(context);
        final Method method = mockClass.getDeclaredMethod(original.getName(), toMockLoader(original.getParameterTypes(), mockClass.getClassLoader()));
        method.setAccessible(true);
        withContextClassLoader(mockClass.getClassLoader(), () -> method.invoke(mockInstance, ctx.getArguments().toArray()));
    }

    private static Class<?>[] toMockLoader(Class<?>[] types, ClassLoader loader) throws ClassNotFoundException {
        Class<?>[] result = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = types[i].isPrimitive() ? types[i] : Class.forName(types[i].getName(), false, loader);
        }
        return result;
    }

    private interface Call {
        Object call() throws Exception;
    }

    private static Object withContextClassLoader(ClassLoader loader, Call call) throws Throwable {
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return call.call();
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /** Identity key: test classes may override equals/hashCode. */
    private static final class InstanceKey {
        private final Object instance;

        InstanceKey(Object instance) {
            this.instance = instance;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof InstanceKey && ((InstanceKey) o).instance == instance;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance);
        }
    }
}
