package org.powermock.modules.junit5.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Processes the mock annotations of a shadow instance inside the MockClassLoader, like PowerMockRunner does:
 * Mockito's {@code @Mock} (PowerMockito mocks, so final/prepared classes work), {@code @Spy}/{@code @InjectMocks}
 * (PowerMockitoInjectingAnnotationEngine), {@code @Captor}, and the EasyMock API's {@code @Mock}/{@code @MockNice}/
 * {@code @MockStrict} (EasyMockAnnotationSupport). Each API is only used if it is on the class path.
 */
final class AnnotationMocks {

    private AnnotationMocks() {
    }

    static void inject(final Object shadow, final ClassLoader loader) throws Exception {
        MockClassLoaderInvoker.withContextClassLoader(loader, () -> {
            injectMockito(shadow, loader);
            injectEasyMock(shadow, loader);
            return null;
        });
    }

    private static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (LinkageError e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectMockito(Object shadow, ClassLoader loader) throws Exception {
        Class<?> powerMockito = load(loader, "org.powermock.api.mockito.PowerMockito");
        Class<?> engine = load(loader, "org.powermock.api.mockito.internal.configuration.PowerMockitoInjectingAnnotationEngine");
        if (powerMockito == null || engine == null) {
            return;
        }
        Class<? extends Annotation> mockAnnotation = (Class<? extends Annotation>) load(loader, "org.mockito.Mock");
        Class<? extends Annotation> captorAnnotation = (Class<? extends Annotation>) load(loader, "org.mockito.Captor");
        Class<?> mockito = load(loader, "org.mockito.Mockito");
        Class<?> mockSettings = load(loader, "org.mockito.MockSettings");
        Class<?> answer = load(loader, "org.mockito.stubbing.Answer");
        Class<?> captor = load(loader, "org.mockito.ArgumentCaptor");
        Method mockWithSettings = powerMockito.getMethod("mock", Class.class, mockSettings);
        for (Class<?> c = shadow.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Annotation mock = field.getAnnotation(mockAnnotation);
                if (mock != null && field.get(shadow) == null) {
                    Object settings = mockito.getMethod("withSettings").invoke(null);
                    Object answers = mock.annotationType().getMethod("answer").invoke(mock);
                    settings = mockSettings.getMethod("defaultAnswer", answer).invoke(settings, answers);
                    String name = (String) mock.annotationType().getMethod("name").invoke(mock);
                    if (name != null && !name.isEmpty()) {
                        settings = mockSettings.getMethod("name", String.class).invoke(settings, name);
                    }
                    Class<?>[] extra = (Class<?>[]) mock.annotationType().getMethod("extraInterfaces").invoke(mock);
                    if (extra != null && extra.length > 0) {
                        settings = mockSettings.getMethod("extraInterfaces", Class[].class).invoke(settings, (Object) extra);
                    }
                    field.set(shadow, mockWithSettings.invoke(null, field.getType(), settings));
                }
                if (field.isAnnotationPresent(captorAnnotation) && field.get(shadow) == null) {
                    field.set(shadow, captor.getMethod("forClass", Class.class).invoke(null, captorType(field)));
                }
            }
        }
        Object engineInstance = engine.getDeclaredConstructor().newInstance();
        engine.getMethod("process", Class.class, Object.class).invoke(engineInstance, shadow.getClass(), shadow);
    }

    private static Class<?> captorType(Field field) {
        java.lang.reflect.Type type = field.getGenericType();
        if (type instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.Type argument = ((java.lang.reflect.ParameterizedType) type).getActualTypeArguments()[0];
            if (argument instanceof java.lang.reflect.ParameterizedType) {
                argument = ((java.lang.reflect.ParameterizedType) argument).getRawType();
            }
            if (argument instanceof Class) {
                return (Class<?>) argument;
            }
        }
        return Object.class;
    }

    private static void injectEasyMock(Object shadow, ClassLoader loader) throws Exception {
        Class<?> support = load(loader, "org.powermock.api.extension.listener.EasyMockAnnotationSupport");
        if (support == null || load(loader, "org.easymock.EasyMock") == null) {
            return;
        }
        Object instance = support.getConstructor(Object.class).newInstance(shadow);
        support.getMethod("injectMocks").invoke(instance);
    }
}
