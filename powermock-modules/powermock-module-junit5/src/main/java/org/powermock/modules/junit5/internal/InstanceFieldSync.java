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
        copy(from, to, new java.util.IdentityHashMap<Object, Boolean>());
    }

    private static void copy(Object from, Object to, java.util.Map<Object, Boolean> seen) {
        if (from == null || to == null || from == to || seen.put(from, Boolean.TRUE) != null) {
            return;
        }
        Class<?> fromClass = from.getClass();
        Class<?> toClass = to.getClass();
        while (fromClass != null && toClass != null && fromClass != Object.class) {
            for (Field source : fromClass.getDeclaredFields()) {
                int mod = source.getModifiers();
                if (Modifier.isStatic(mod) || source.isSynthetic()) {
                    continue;
                }
                try {
                    Field target = toClass.getDeclaredField(source.getName());
                    source.setAccessible(true);
                    target.setAccessible(true);
                    if (target.getType() != source.getType() || Modifier.isFinal(mod)) {
                        // same field, different loaders (e.g. a @RegisterExtension instance): mirror the
                        // state of the object the other side holds instead of replacing it
                        Object fromValue = source.get(from);
                        Object toValue = target.get(to);
                        if (fromValue != null && toValue != null && fromValue != toValue
                            && fromValue.getClass().getName().equals(toValue.getClass().getName())
                            && fromValue.getClass() != toValue.getClass()) {
                            copy(fromValue, toValue, seen);
                        }
                        continue;
                    }
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
