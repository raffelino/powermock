package org.powermock.modules.junit5.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Keeps the instance fields of Jupiter's test instance and its MockClassLoader shadow in step, so other
 * extensions (TestInstancePostProcessor, BeforeEach/AfterEach callbacks) that set or read test instance state
 * see what the test code sees. Values set by other extensions go in (non-null values only, so mocks injected
 * into the shadow are not overwritten); after each invocation the shadow's values come back where the types
 * allow it.
 */
final class FieldSync {

    private FieldSync() {
    }

    static void originalToShadow(Object original, Object shadow, ClassLoader loader) throws IllegalAccessException {
        for (Class<?> c = original.getClass(), s = shadow.getClass(); c != null && s != null && c != Object.class;
             c = c.getSuperclass(), s = s.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (skip(field)) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(original);
                if (value == null) {
                    continue;
                }
                Field shadowField = fieldOrNull(s, field.getName());
                if (shadowField == null) {
                    continue;
                }
                Object current = shadowField.get(shadow);
                if (current == value) {
                    continue;
                }
                Object converted = ClassLoaderBridge.toLoader(value, loader);
                if (isAssignable(shadowField, converted)) {
                    shadowField.set(shadow, converted);
                }
            }
        }
    }

    static void shadowToOriginal(Object shadow, Object original) throws IllegalAccessException {
        shadowToOriginal(shadow, original, false);
    }

    /**
     * After the shadow is created: hand the mocks injected by annotation processing to Jupiter's instance, so
     * that their initializer values (e.g. the object behind a @Spy) do not later overwrite the shadow's.
     */
    static void annotatedMocksToOriginal(Object shadow, Object original) throws IllegalAccessException {
        shadowToOriginal(shadow, original, true);
    }

    private static boolean isMockAnnotated(Field field) {
        for (java.lang.annotation.Annotation annotation : field.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if (name.startsWith("Mock") || name.equals("Spy") || name.equals("Captor") || name.equals("InjectMocks")
                || name.equals("TestSubject")) {
                return true;
            }
        }
        return false;
    }

    private static void shadowToOriginal(Object shadow, Object original, boolean onlyMocks) throws IllegalAccessException {
        for (Class<?> c = original.getClass(), s = shadow.getClass(); c != null && s != null && c != Object.class;
             c = c.getSuperclass(), s = s.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (skip(field) || (onlyMocks && !isMockAnnotated(field))) {
                    continue;
                }
                Field shadowField = fieldOrNull(s, field.getName());
                if (shadowField == null) {
                    continue;
                }
                Object value = shadowField.get(shadow);
                field.setAccessible(true);
                if (isAssignable(field, value)) {
                    field.set(original, value);
                }
            }
        }
    }

    private static boolean skip(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic();
    }

    private static boolean isAssignable(Field field, Object value) {
        if (value == null) {
            return !field.getType().isPrimitive();
        }
        Class<?> type = field.getType();
        if (type.isPrimitive()) {
            return true; // boxed value of the same primitive field
        }
        return type.isInstance(value);
    }

    private static Field fieldOrNull(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
