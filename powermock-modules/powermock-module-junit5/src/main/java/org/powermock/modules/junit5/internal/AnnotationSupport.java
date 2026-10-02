package org.powermock.modules.junit5.internal;

import java.lang.reflect.InvocationTargetException;

/**
 * Injects annotated mocks into a shadow instance inside the MockClassLoader: Mockito's annotations
 * (if Mockito / powermock-api-mockito2 is on the classpath) and PowerMock's EasyMock annotations
 * (if powermock-api-easymock is on the classpath).
 */
final class AnnotationSupport {

    private static final String MOCKITO_INJECTOR = "org.powermock.modules.junit5.internal.inloader.MockitoAnnotationInjector";
    private static final String EASYMOCK_SUPPORT = "org.powermock.api.extension.listener.EasyMockAnnotationSupport";

    private AnnotationSupport() {
    }

    static void injectMocks(final ClassLoader classLoader, final Object shadow) throws Exception {
        try {
            MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
                if (isPresent("org.powermock.api.mockito.PowerMockito", classLoader)) {
                    Class.forName(MOCKITO_INJECTOR, true, classLoader).getMethod("inject", Object.class).invoke(null, shadow);
                }
                if (isPresent(EASYMOCK_SUPPORT, classLoader)) {
                    Object support = Class.forName(EASYMOCK_SUPPORT, true, classLoader).getConstructor(Object.class).newInstance(shadow);
                    support.getClass().getMethod("injectMocks").invoke(support);
                }
                return null;
            });
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }

    private static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            return false;
        }
    }
}
