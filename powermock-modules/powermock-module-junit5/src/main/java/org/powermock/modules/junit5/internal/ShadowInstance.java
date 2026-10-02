package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Jupiter test instance and its "shadow" (same class loaded by the MockClassLoader) with two-way
 * synchronisation of instance fields: values written on one side since the last synchronisation are
 * copied (converted across the class-loader boundary) to the other side. This lets Jupiter and other
 * extensions (which see the original instance) and the test code (which runs on the shadow) share state.
 */
public final class ShadowInstance {

    private final Object original;
    private final Object shadow;
    private final List<Field[]> fields = new ArrayList<Field[]>();
    private final Map<Field, Object> lastOriginal = new HashMap<Field, Object>();
    private final Map<Field, Object> lastShadow = new HashMap<Field, Object>();

    ShadowInstance(Object original, Object shadow) {
        this.original = original;
        this.shadow = shadow;
        Class<?> originalType = original.getClass();
        Class<?> shadowType = shadow.getClass();
        while (originalType != null && shadowType != null && originalType != Object.class) {
            for (Field field : originalType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    Field shadowField = shadowType.getDeclaredField(field.getName());
                    field.setAccessible(true);
                    shadowField.setAccessible(true);
                    fields.add(new Field[]{field, shadowField});
                } catch (NoSuchFieldException e) {
                    // not present in the MockClassLoader's version of the class
                }
            }
            originalType = originalType.getSuperclass();
            shadowType = shadowType.getSuperclass();
        }
        shareRegisteredExtensions();
        markSynchronised();
    }

    /**
     * @RegisterExtension field types are not reloaded by the MockClassLoader: the shadow uses the very
     * extension object Jupiter calls.
     */
    private void shareRegisteredExtensions() {
        for (Field[] pair : fields) {
            if (pair[0].isAnnotationPresent(RegisterExtension.class)) {
                set(pair[1], shadow, get(pair[0], original));
            }
        }
    }

    public Object getOriginal() {
        return original;
    }

    public Object getShadow() {
        return shadow;
    }

    /** Takes the current values as the common baseline (nothing is copied). */
    void markSynchronised() {
        for (Field[] pair : fields) {
            lastOriginal.put(pair[0], get(pair[0], original));
            lastShadow.put(pair[0], get(pair[1], shadow));
        }
    }

    /** Copies fields changed on the original since the last synchronisation into the shadow. */
    public void toShadow(ObjectTransfer.KnownCounterparts known) {
        ClassLoader loader = shadow.getClass().getClassLoader();
        for (Field[] pair : fields) {
            Object value = get(pair[0], original);
            if (value == lastOriginal.get(pair[0])) {
                continue;
            }
            Object converted = ObjectTransfer.transfer(value, loader, known);
            if (set(pair[1], shadow, converted)) {
                lastShadow.put(pair[0], converted);
            }
            lastOriginal.put(pair[0], value);
        }
    }

    /** Copies fields changed on the shadow since the last synchronisation into the original. */
    public void toOriginal(ObjectTransfer.KnownCounterparts known) {
        ClassLoader loader = original.getClass().getClassLoader();
        for (Field[] pair : fields) {
            Object value = get(pair[1], shadow);
            if (value == lastShadow.get(pair[0])) {
                continue;
            }
            Object converted;
            try {
                converted = ObjectTransfer.transfer(value, loader, known);
            } catch (RuntimeException e) {
                converted = value;
            }
            if (set(pair[0], original, converted)) {
                lastOriginal.put(pair[0], converted);
            }
            lastShadow.put(pair[0], value);
        }
    }

    private static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean set(Field field, Object target, Object value) {
        if (value != null && !ObjectTransfer.boxed(field.getType()).isInstance(value)) {
            return false;
        }
        if (value == null && field.getType().isPrimitive()) {
            return false;
        }
        try {
            field.set(target, value);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
