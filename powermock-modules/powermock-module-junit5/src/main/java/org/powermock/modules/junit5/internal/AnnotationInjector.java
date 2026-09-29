package org.powermock.modules.junit5.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Processes the mocking API's field annotations (Mockito @Mock/@Spy/@Captor/@InjectMocks or EasyMock
 * @Mock/@MockNice/@MockStrict) on a shadow instance, like the JUnit 4 runner does: the API's
 * {@code org.powermock.api.extension.listener.AnnotationEnabler} is loaded by the MockClassLoader.
 */
public class AnnotationInjector {

    private static final String ANNOTATION_ENABLER = "org.powermock.api.extension.listener.AnnotationEnabler";

    public static void inject(ClassLoader mockClassLoader, Object shadowInstance) throws Exception {
        final Class<?> enablerClass;
        try {
            enablerClass = Class.forName(ANNOTATION_ENABLER, true, mockClassLoader);
        } catch (ClassNotFoundException e) {
            return; // no PowerMock mocking API on the class path
        }
        final Object enabler = enablerClass.getDeclaredConstructor().newInstance();
        final Method beforeTestMethod = enablerClass.getMethod("beforeTestMethod", Object.class, Method.class, Object[].class);
        try {
            MockClassLoaderInvoker.withContextClassLoader(mockClassLoader,
                () -> beforeTestMethod.invoke(enabler, shadowInstance, null, new Object[0]));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }
}
