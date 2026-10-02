package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two-way synchronisation of instance fields between Jupiter's test instance and its shadow in the MockClassLoader,
 * so extensions (TestInstancePostProcessor, callbacks, @TempDir ...) and the test code see each other's writes.
 * <p>
 * Before an invocation, fields of Jupiter's instance that changed since the last synchronisation are copied into
 * the shadow; after it, shadow fields whose values can be shared are copied back. Fields processed by the mock
 * annotations are owned by the shadow and never synchronised (a mock from the application loader's view is useless
 * for code running in the MockClassLoader).
 */
class FieldSync {

    private static final Set<String> SHADOW_OWNED_ANNOTATIONS = new HashSet<String>(Arrays.asList(
        "Mock", "Spy", "Captor", "InjectMocks", "MockNice", "MockStrict", "TestSubject"));

    private final TestClassInClassLoader loaded;
    private final Map<Object, Map<Field, Object>> snapshots = new IdentityHashMap<Object, Map<Field, Object>>();

    FieldSync(TestClassInClassLoader loaded) {
        this.loaded = loaded;
    }

    synchronized void toShadow(Object original) throws Exception {
        Object shadow = loaded.getShadowInstance(original);
        Map<Field, Object> snapshot = snapshots.get(original);
        CrossLoader transfer = loaded.sharingToMockClassLoader();
        for (Field field : fields(original.getClass())) {
            Object value = field.get(original);
            if (snapshot == null ? value == null : sameValue(snapshot.get(field), value)) {
                continue;
            }
            Object transferred = transfer.transfer(value);
            if (transferred == CrossLoader.NOT_SHAREABLE) {
                continue;
            }
            Field shadowField = shadowField(shadow, field);
            if (shadowField != null && isAssignable(shadowField, transferred)) {
                shadowField.set(shadow, transferred);
            }
        }
        snapshot(original);
    }

    synchronized void toOriginal(Object original) throws Exception {
        Object shadow = loaded.getShadowInstance(original);
        CrossLoader transfer = loaded.sharingToApplication(original.getClass().getClassLoader());
        for (Field field : fields(original.getClass())) {
            Field shadowField = shadowField(shadow, field);
            if (shadowField == null) {
                continue;
            }
            Object transferred = transfer.transfer(shadowField.get(shadow));
            if (transferred == CrossLoader.NOT_SHAREABLE || !isAssignable(field, transferred)
                || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.set(original, transferred);
        }
        snapshot(original);
    }

    private void snapshot(Object original) throws IllegalAccessException {
        Map<Field, Object> snapshot = new HashMap<Field, Object>();
        for (Field field : fields(original.getClass())) {
            snapshot.put(field, field.get(original));
        }
        snapshots.put(original, snapshot);
    }

    private static boolean sameValue(Object a, Object b) {
        if (a == b) {
            return true;
        }
        // boxed primitives / strings: compare by value
        return a != null && b != null && (a instanceof Number || a instanceof String || a instanceof Boolean
            || a instanceof Character) && a.equals(b);
    }

    private static boolean isAssignable(Field field, Object value) {
        if (value == null) {
            return !field.getType().isPrimitive();
        }
        if (field.getType().isPrimitive()) {
            return true;
        }
        return field.getType().isInstance(value);
    }

    private static Field shadowField(Object shadow, Field field) {
        String declaringName = field.getDeclaringClass().getName();
        for (Class<?> c = shadow.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(declaringName)) {
                try {
                    Field f = c.getDeclaredField(field.getName());
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<Field>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || f.isSynthetic() || isShadowOwned(f)
                    || Modifier.isFinal(m) && !f.isAnnotationPresent(RegisterExtension.class)) {
                    continue;
                }
                f.setAccessible(true);
                result.add(f);
            }
        }
        return result;
    }

    private static boolean isShadowOwned(Field field) {
        for (Annotation a : field.getAnnotations()) {
            if (SHADOW_OWNED_ANNOTATIONS.contains(a.annotationType().getSimpleName())) {
                return true;
            }
        }
        return false;
    }
}
