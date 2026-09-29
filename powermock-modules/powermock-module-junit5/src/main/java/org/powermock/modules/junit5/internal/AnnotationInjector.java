package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Processes mock annotations (@Mock, @Spy, @Captor, @InjectMocks ...) on the shadow instance, inside the
 * MockClassLoader, using the API module's AnnotationEnabler exactly like the JUnit 4 runner does.
 */
public class AnnotationInjector {

    private static final String ANNOTATION_ENABLER = "org.powermock.api.extension.listener.AnnotationEnabler";

    public static void inject(final TestClassInClassLoader loaded, ExtensionContext context) throws Throwable {
        final Object shadow = loaded.getShadowInstance(context.getRequiredTestInstance(), context);
        final Class<?> enablerClass;
        try {
            enablerClass = Class.forName(ANNOTATION_ENABLER, true, loaded.getClassLoader());
        } catch (ClassNotFoundException e) {
            return; // no PowerMock API module with annotation support on the class path
        }
        try {
            MockClassLoaderInvoker.withContextClassLoader(loaded.getClassLoader(), () -> {
                Object enabler = enablerClass.getDeclaredConstructor().newInstance();
                Method beforeTestMethod = enablerClass.getMethod("beforeTestMethod", Object.class, Method.class, Object[].class);
                return beforeTestMethod.invoke(enabler, shadow, null, new Object[0]);
            });
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
