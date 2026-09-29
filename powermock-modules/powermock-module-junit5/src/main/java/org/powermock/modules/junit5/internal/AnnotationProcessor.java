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
    private static final String EASYMOCK_SUPPORT = "org.powermock.api.extension.listener.EasyMockAnnotationSupport";

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
                // Both API modules ship a class named ENABLER; when both are on the classpath only one wins,
                // so the EasyMock annotations are additionally processed directly.
                try {
                    Class<?> easyMock = Class.forName(EASYMOCK_SUPPORT, true, cl);
                    easyMock.getMethod("injectMocks").invoke(easyMock.getConstructor(Object.class).newInstance(shadow));
                } catch (ClassNotFoundException ignored) {
                    // EasyMock API not on the classpath
                }
                return null;
            });
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
