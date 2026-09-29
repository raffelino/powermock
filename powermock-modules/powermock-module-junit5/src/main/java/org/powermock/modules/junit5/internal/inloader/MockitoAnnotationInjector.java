package org.powermock.modules.junit5.internal.inloader;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.util.reflection.GenericMaster;
import org.powermock.api.mockito.internal.configuration.PowerMockitoInjectingAnnotationEngine;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Field;

import static org.mockito.Mockito.withSettings;
import static org.powermock.api.mockito.PowerMockito.mock;

/**
 * Processes Mockito's annotations (@Mock, @Spy, @Captor, @InjectMocks) on a test instance the way
 * PowerMockRunner's AnnotationEnabler does. This class is loaded and executed INSIDE the MockClassLoader
 * (by name, reflectively), so it uses the MockClassLoader's Mockito and PowerMockito. We do not use
 * {@code org.powermock.api.extension.listener.AnnotationEnabler} directly because powermock-api-easymock
 * has a class with the same name.
 */
public final class MockitoAnnotationInjector {

    private MockitoAnnotationInjector() {
    }

    public static void inject(Object testInstance) throws Exception {
        for (Field field : Whitebox.getFieldsAnnotatedWith(testInstance, Mock.class)) {
            if (field.get(testInstance) != null) {
                continue;
            }
            Mock annotation = field.getAnnotation(Mock.class);
            MockSettings settings = withSettings();
            if (annotation.answer() != null) {
                settings.defaultAnswer(annotation.answer());
            }
            Class<?>[] extraInterfaces = annotation.extraInterfaces();
            if (extraInterfaces != null && extraInterfaces.length > 0) {
                settings.extraInterfaces(extraInterfaces);
            }
            if (annotation.name() != null && annotation.name().length() > 0) {
                settings.name(annotation.name());
            }
            field.set(testInstance, mock(field.getType(), settings));
        }
        new PowerMockitoInjectingAnnotationEngine().process(testInstance.getClass(), testInstance);
        for (Field field : Whitebox.getFieldsAnnotatedWith(testInstance, Captor.class)) {
            if (!ArgumentCaptor.class.isAssignableFrom(field.getType())) {
                throw new MockitoException("@Captor field must be of the type ArgumentCaptor. Field: '"
                    + field.getName() + "' has wrong type");
            }
            field.set(testInstance, ArgumentCaptor.forClass(new GenericMaster().getGenericType(field)));
        }
    }
}
