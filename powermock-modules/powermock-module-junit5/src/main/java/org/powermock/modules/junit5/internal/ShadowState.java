package org.powermock.modules.junit5.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Jupiter test instance and its shadow in the MockClassLoader. Instance fields are synchronised both ways
 * around every redirected invocation, so extensions that work on Jupiter's instance (TestInstancePostProcessor,
 * Before/AfterEachCallback, @TempDir, ...) and the test code running on the shadow see each other's writes.
 * <p>
 * Only values that are the same object on both sides are copied (JDK and other shared classes, and test
 * instances which are mapped to their shadow / original). Objects of MockClassLoader-only classes (mocks,
 * prepared classes) stay on their side.
 */
public class ShadowState {

    private final Object original;
    private final Object shadow;
    private final List<Field[]> fieldPairs = new ArrayList<Field[]>();
    /** Values of the original's fields at the last synchronisation: only fields changed since are copied to the shadow. */
    private final Map<Field, Object> snapshot = new HashMap<Field, Object>();

    ShadowState(Object original, Object shadow) {
        this.original = original;
        this.shadow = shadow;
        Class<?> shadowType = shadow.getClass();
        for (Class<?> type = original.getClass(); type != null && type != Object.class;
             type = type.getSuperclass(), shadowType = shadowType.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    Field shadowField = shadowType.getDeclaredField(field.getName());
                    field.setAccessible(true);
                    shadowField.setAccessible(true);
                    fieldPairs.add(new Field[]{field, shadowField});
                } catch (NoSuchFieldException e) {
                    // different class hierarchy (should not happen): nothing to synchronise
                }
            }
        }
        takeSnapshot();
    }

    public Object getOriginal() {
        return original;
    }

    public Object getShadow() {
        return shadow;
    }

    /** Copies fields changed on Jupiter's instance since the last synchronisation to the shadow. */
    void copyToShadow(ObjectTransfer transfer) throws IllegalAccessException {
        for (Field[] pair : fieldPairs) {
            Object value = pair[0].get(original);
            if (snapshot.containsKey(pair[0]) && snapshot.get(pair[0]) == value) {
                continue;
            }
            Object converted = transfer.sharedValueInMockClassLoader(value);
            if (converted != ObjectTransfer.NOT_SHARED && isAssignable(pair[1], converted)) {
                pair[1].set(shadow, converted);
            }
        }
        takeSnapshot();
    }

    /** Copies the shadow's fields back to Jupiter's instance (where the value can live there). */
    void copyToOriginal(ObjectTransfer transfer) throws IllegalAccessException {
        for (Field[] pair : fieldPairs) {
            Object value = pair[1].get(shadow);
            Object converted = transfer.sharedValueOutsideMockClassLoader(value, original.getClass().getClassLoader());
            if (converted != ObjectTransfer.NOT_SHARED && isAssignable(pair[0], converted)) {
                pair[0].set(original, converted);
            }
        }
        takeSnapshot();
    }

    private void takeSnapshot() {
        for (Field[] pair : fieldPairs) {
            try {
                snapshot.put(pair[0], pair[0].get(original));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean isAssignable(Field field, Object value) {
        if (value == null) {
            return !field.getType().isPrimitive();
        }
        if (field.getType().isPrimitive()) {
            return true; // boxed value of the same primitive field type
        }
        return field.getType().isInstance(value);
    }
}
