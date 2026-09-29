package org.powermock.modules.junit5.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Copies instance field values between Jupiter's test instance and its shadow instance, so that other
 * extensions (TestInstancePostProcessor, Before/AfterEachCallback) and the test code see the same state.
 * Only fields whose declared type is the same class in both loaders (e.g. JDK types) are copied.
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
                int mod = source.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || source.isSynthetic()) {
                    continue;
                }
                try {
                    Field target = toClass.getDeclaredField(source.getName());
                    if (target.getType() != source.getType()) {
                        continue;
                    }
                    source.setAccessible(true);
                    target.setAccessible(true);
                    Object value = source.get(from);
                    if (value != null) { // never clobber state set up on the other side (e.g. injected mocks)
                        target.set(to, value);
                    }
                } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
                    // field not transferable; skip
                }
            }
            fromClass = fromClass.getSuperclass();
            toClass = toClass.getSuperclass();
        }
    }
}
