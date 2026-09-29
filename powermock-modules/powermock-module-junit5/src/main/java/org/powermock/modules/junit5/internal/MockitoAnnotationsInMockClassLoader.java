package org.powermock.modules.junit5.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Processes Mockito annotations (@Mock, @Spy, @Captor, @InjectMocks) on a shadow instance with PowerMock's
 * AnnotationEnabler loaded by the MockClassLoader, as PowerMockRunner does. No-op when no PowerMock API module
 * that provides the AnnotationEnabler is on the class path.
 */
public class MockitoAnnotationsInMockClassLoader {

    private static final String ANNOTATION_ENABLER = "org.powermock.api.extension.listener.AnnotationEnabler";

    public static void process(ClassLoader mockClassLoader, Object shadowInstance) throws Exception {
        final Class<?> enablerClass;
        try {
            enablerClass = Class.forName(ANNOTATION_ENABLER, true, mockClassLoader);
        } catch (ClassNotFoundException e) {
            return;
        }
        final Object enabler = enablerClass.getConstructor().newInstance();
        final Method beforeTestMethod = enablerClass.getMethod("beforeTestMethod", Object.class, Method.class, Object[].class);
        try {
            MockClassLoaderInvoker.withContextClassLoader(mockClassLoader,
                () -> beforeTestMethod.invoke(enabler, shadowInstance, null, new Object[0]));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }
}
