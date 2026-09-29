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
                    if (value != null && (type.isPrimitive() || type.isInstance(value))) {
                        // nulls are never copied: a null on one side means "not transferable", and copying it would wipe
                        // fields injected on the other side (e.g. @Mock fields set on the shadow instance)
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

    /**
     * Like {@link #copy(Object, Object)}, but also syncs the enclosing instances of @Nested test classes
     * (reached through the synthetic {@code this$N} fields), so state written into an outer instance by a
     * nested test is seen by the outer class's lifecycle methods.
     */
    static void copyWithEnclosing(Object from, Object to) {
        while (from != null && to != null && from != to) {
            copy(from, to);
            Object nextFrom = enclosing(from);
            Object nextTo = enclosing(to);
            from = nextFrom;
            to = nextTo;
        }
    }

    private static Object enclosing(Object instance) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.isSynthetic() && field.getName().startsWith("this$")) {
                try {
                    field.setAccessible(true);
                    return field.get(instance);
                } catch (IllegalAccessException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
