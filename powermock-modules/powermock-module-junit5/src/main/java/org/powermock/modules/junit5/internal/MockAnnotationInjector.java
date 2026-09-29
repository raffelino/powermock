package org.powermock.modules.junit5.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Processes Mockito's field annotations (@Mock, @Spy, @Captor, @InjectMocks) on the MockClassLoader-loaded
 * shadow instance, using the Mockito classes loaded by the MockClassLoader (so final/prepared classes can be
 * mocked), like PowerMockRunner does before each test.
 */
public class MockAnnotationInjector {

    public static void inject(ClassLoader classLoader, Object shadow) throws Throwable {
        final Method init;
        try {
            Class<?> annotations = Class.forName("org.mockito.MockitoAnnotations", true, classLoader);
            init = findInit(annotations);
        } catch (ClassNotFoundException e) {
            return; // Mockito not on the class path
        }
        try {
            MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> init.invoke(null, shadow));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Method findInit(Class<?> annotations) throws NoSuchMethodException {
        try {
            return annotations.getMethod("openMocks", Object.class);
        } catch (NoSuchMethodException e) {
            return annotations.getMethod("initMocks", Object.class);
        }
    }
}
