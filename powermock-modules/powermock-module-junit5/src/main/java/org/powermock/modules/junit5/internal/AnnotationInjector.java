package org.powermock.modules.junit5.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Processes the mocking API's field annotations (Mockito @Mock/@Spy/@Captor/@InjectMocks or EasyMock
 * @Mock/@MockNice/@MockStrict) on a shadow instance, like the JUnit 4 runner does: the API's
 * {@code org.powermock.api.extension.listener.AnnotationEnabler} is loaded by the MockClassLoader.
 */
public class AnnotationInjector {

    // Both APIs ship org.powermock.api.extension.listener.AnnotationEnabler (only one wins on a shared
    // class path), EasyMock additionally ships its own subclass under a unique name.
    private static final String[] ANNOTATION_ENABLERS = {
        "org.powermock.api.extension.listener.AnnotationEnabler"
    };

    public static void inject(ClassLoader mockClassLoader, Object shadowInstance) throws Exception {
        for (String enabler : ANNOTATION_ENABLERS) {
            inject(mockClassLoader, shadowInstance, enabler);
        }
        injectEasyMock(mockClassLoader, shadowInstance);
    }

    // The EasyMock enabler shares its class name with Mockito's, so inject EasyMock annotations directly.
    private static void injectEasyMock(ClassLoader cl, Object instance) throws Exception {
        final Class<?> powerMock;
        try {
            powerMock = Class.forName("org.powermock.api.easymock.PowerMock", true, cl);
        } catch (ClassNotFoundException e) {
            return;
        }
        String[][] kinds = {
            {"org.powermock.api.easymock.annotation.Mock", "createMock"},
            {"org.powermock.api.easymock.annotation.MockNice", "createNiceMock"},
            {"org.powermock.api.easymock.annotation.MockStrict", "createStrictMock"}};
        for (Class<?> c = instance.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                for (String[] kind : kinds) {
                    @SuppressWarnings("unchecked")
                    Class<? extends java.lang.annotation.Annotation> a =
                        (Class<? extends java.lang.annotation.Annotation>) Class.forName(kind[0], true, cl);
                    if (field.isAnnotationPresent(a)) {
                        field.setAccessible(true);
                        // The single-argument variants create full mocks; createMock(type, new Method[0]) would
                        // create a partial mock that mocks no method at all ("no last call on a mock available").
                        final Method create = powerMock.getMethod(kind[1], Class.class);
                        final Object mock = MockClassLoaderInvoker.withContextClassLoader(cl,
                            () -> create.invoke(null, field.getType()));
                        field.set(instance, mock);
                    }
                }
            }
        }
    }

    private static void inject(ClassLoader mockClassLoader, Object shadowInstance, String enablerName) throws Exception {
        final Class<?> enablerClass;
        try {
            enablerClass = Class.forName(enablerName, true, mockClassLoader);
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
