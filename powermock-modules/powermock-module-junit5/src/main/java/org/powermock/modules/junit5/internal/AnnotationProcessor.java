package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Processes mock annotations (Mockito's @Mock/@Spy/@Captor/@InjectMocks or EasyMock's @Mock/@MockNice/@MockStrict)
 * on the shadow instance before each test, the same way PowerMockRunner does via the API's AnnotationEnabler.
 */
public class AnnotationProcessor {

    private static final String ENABLER = "org.powermock.api.extension.listener.AnnotationEnabler";

    public static void beforeEach(ExtensionContext context) throws Throwable {
        final TestClassInClassLoader loaded = TestClassInClassLoader.of(context);
        final ClassLoader cl = loaded.getClassLoader();
        final Class<?> enablerClass;
        try {
            enablerClass = Class.forName(ENABLER, true, cl);
        } catch (ClassNotFoundException e) {
            return;
        }
        final Object shadow = loaded.getShadowInstance(context.getRequiredTestInstance(), context);
        final Method testMethod = MockClassLoaderInvoker.findMethod(cl, context.getRequiredTestMethod());
        try {
            MockClassLoaderInvoker.withContextClassLoader(cl, () -> {
                Object enabler = enablerClass.getConstructor().newInstance();
                Method before = enablerClass.getMethod("beforeTestMethod", Object.class, Method.class, Object[].class);
                before.invoke(enabler, shadow, testMethod, new Object[0]);
                return null;
            });
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
