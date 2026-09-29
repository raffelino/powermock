package org.powermock.modules.junit5.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Copies instance field values between Jupiter's test instance and the MockClassLoader shadow instance
 * (both directions), so other extensions that read or write test instance state see the same values
 * the test code sees. Only values assignable to the target field's type are copied.
 */
final class InstanceFieldSync {

    private InstanceFieldSync() {
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
                    Class<?> type = target.getType();
                    if (type.isPrimitive() || value == null || type.isInstance(value)) {
                        target.set(to, value);
                    }
                } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
                    // not transferable across the class-loader boundary
                }
            }
            fromClass = fromClass.getSuperclass();
            toClass = toClass.getSuperclass();
        }
    }
}
