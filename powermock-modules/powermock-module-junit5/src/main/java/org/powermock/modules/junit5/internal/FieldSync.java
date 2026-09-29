package org.powermock.modules.junit5.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Copies instance field values between the original test instance and its shadow in the MockClassLoader,
 * so that other extensions (which see the original instance) and the test code (which runs on the shadow)
 * see each other's state. Only values assignable to the target field's type are copied.
 */
final class FieldSync {

    private FieldSync() {
    }

    static void copy(Object from, Object to) {
        if (from == null || to == null || from == to) {
            return;
        }
        Class<?> fromClass = from.getClass();
        Class<?> toClass = to.getClass();
        while (fromClass != null && toClass != null && fromClass != Object.class) {
            for (Field source : fromClass.getDeclaredFields()) {
                if (Modifier.isStatic(source.getModifiers()) || source.isSynthetic()) {
                    continue;
                }
                try {
                    Field target = toClass.getDeclaredField(source.getName());
                    if (Modifier.isStatic(target.getModifiers())) {
                        continue;
                    }
                    source.setAccessible(true);
                    target.setAccessible(true);
                    if (isRegisteredExtension(source)) {
                        // @RegisterExtension objects exist once per side (different loaders): share their state
                        Object value = source.get(from);
                        Object targetValue = target.get(to);
                        if (value != null && targetValue != null && value != targetValue
                            && value.getClass().getName().equals(targetValue.getClass().getName())) {
                            copyState(value, targetValue);
                        }
                        continue;
                    }
                    if (Modifier.isFinal(target.getModifiers())) {
                        continue;
                    }
                    Object value = source.get(from);
                    if (value == null) {
                        // never wipe state that exists only on one side (e.g. EasyMock mocks injected into the shadow)
                        continue;
                    }
                    if (isAssignable(target.getType(), value)) {
                        target.set(to, value);
                    } else if (value != null && target.get(to) == null && isMockitoMock(source)) {
                        // another extension (e.g. MockitoExtension) mocked a type that differs across loaders:
                        // give the shadow its own mock of the MockClassLoader's version of the type
                        target.set(to, mockInLoader(target.getType()));
                    }
                } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
                    // field not present or not accessible on the other side: leave it alone
                }
            }
            fromClass = fromClass.getSuperclass();
            toClass = toClass.getSuperclass();
        }
    }

    private static boolean isRegisteredExtension(Field field) {
        for (java.lang.annotation.Annotation a : field.getAnnotations()) {
            if (a.annotationType().getName().equals("org.junit.jupiter.api.extension.RegisterExtension")) {
                return true;
            }
        }
        return false;
    }

    /** Shallow copy of all instance fields (incl. final ones) whose values are assignable on the other side. */
    private static void copyState(Object from, Object to) {
        Class<?> fromClass = from.getClass();
        Class<?> toClass = to.getClass();
        while (fromClass != null && toClass != null && fromClass != Object.class) {
            for (Field source : fromClass.getDeclaredFields()) {
                if (Modifier.isStatic(source.getModifiers())) {
                    continue;
                }
                try {
                    Field target = toClass.getDeclaredField(source.getName());
                    source.setAccessible(true);
                    target.setAccessible(true);
                    Object value = source.get(from);
                    if (value == null ? !target.getType().isPrimitive() : isAssignable(target.getType(), value)) {
                        target.set(to, value);
                    }
                } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
                    // not shareable across the loaders: leave it alone
                }
            }
            fromClass = fromClass.getSuperclass();
            toClass = toClass.getSuperclass();
        }
    }

    private static boolean isMockitoMock(Field field) {
        for (java.lang.annotation.Annotation a : field.getAnnotations()) {
            String name = a.annotationType().getName();
            if (name.equals("org.mockito.Mock") || name.equals("org.mockito.Spy")) {
                return true;
            }
        }
        return false;
    }

    private static Object mockInLoader(final Class<?> type) throws IllegalAccessException {
        final ClassLoader loader = type.getClassLoader();
        try {
            return MockClassLoaderInvoker.withContextClassLoader(loader, () ->
                Class.forName("org.powermock.api.mockito.PowerMockito", true, loader)
                    .getMethod("mock", Class.class).invoke(null, type));
        } catch (Exception e) {
            throw new IllegalAccessException("Cannot create mock of " + type + ": " + e);
        }
    }

    private static boolean isAssignable(Class<?> type, Object value) {
        if (type.isPrimitive()) {
            return true; // Field.set unwraps; mismatches throw IllegalArgumentException and are skipped
        }
        return type.isInstance(value);
    }
}
