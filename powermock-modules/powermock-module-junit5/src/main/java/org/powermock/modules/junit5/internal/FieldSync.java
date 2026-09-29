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
                    if (Modifier.isStatic(target.getModifiers()) || Modifier.isFinal(target.getModifiers())) {
                        continue;
                    }
                    source.setAccessible(true);
                    target.setAccessible(true);
                    Object value = source.get(from);
                    if (value == null ? !target.getType().isPrimitive() : isAssignable(target.getType(), value)) {
                        target.set(to, value);
                    }
                } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
                    // field not present or not accessible on the other side: leave it alone
                }
            }
            fromClass = fromClass.getSuperclass();
            toClass = toClass.getSuperclass();
        }
    }

    private static boolean isAssignable(Class<?> type, Object value) {
        if (type.isPrimitive()) {
            return true; // Field.set unwraps; mismatches throw IllegalArgumentException and are skipped
        }
        return type.isInstance(value);
    }
}
