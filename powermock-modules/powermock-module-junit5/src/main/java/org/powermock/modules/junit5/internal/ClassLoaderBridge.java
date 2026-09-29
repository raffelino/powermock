package org.powermock.modules.junit5.internal;

import org.powermock.reflect.Whitebox;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Converts objects resolved by Jupiter (in the original class loader) into equivalents whose classes
 * are loaded by the MockClassLoader, so they behave like objects of prepared classes.
 */
public final class ClassLoaderBridge {

    private ClassLoaderBridge() {
    }

    public static Object convert(Object value, ClassLoader classLoader) {
        return convert(value, classLoader, new IdentityHashMap<Object, Object>());
    }

    public static Object allocate(Class<?> type) {
        return Whitebox.newInstance(type);
    }

    public static void copyFields(Object from, Object to, ClassLoader classLoader) {
        copyFields(from, to, classLoader, new IdentityHashMap<Object, Object>(), false);
    }

    public static void copyAnnotatedFields(Object from, Object to, ClassLoader classLoader) {
        copyFields(from, to, classLoader, new IdentityHashMap<Object, Object>(), true);
    }

    private static Object convert(Object value, ClassLoader classLoader, Map<Object, Object> done) {
        if (value == null) {
            return null;
        }
        if (value instanceof Class) {
            return loadEquivalent((Class<?>) value, classLoader);
        }
        Class<?> type = value.getClass();
        if (value instanceof Enum) {
            Class<?> enumType = ((Enum<?>) value).getDeclaringClass();
            Class<?> loaded = loadEquivalent(enumType, classLoader);
            if (loaded == enumType) {
                return value;
            }
            for (Object constant : loaded.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(((Enum<?>) value).name())) {
                    return constant;
                }
            }
            return value;
        }
        if (type.isArray()) {
            Class<?> loadedArray = loadEquivalent(type, classLoader);
            int length = Array.getLength(value);
            if (loadedArray == type && type.getComponentType().isPrimitive()) {
                return value;
            }
            Object copy = Array.newInstance(loadedArray.getComponentType(), length);
            done.put(value, copy);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, convert(Array.get(value, i), classLoader, done));
            }
            return copy;
        }
        Class<?> loaded = loadEquivalent(type, classLoader);
        if (loaded == type) {
            return value;
        }
        Object existing = done.get(value);
        if (existing != null) {
            return existing;
        }
        Object copy = Whitebox.newInstance(loaded);
        done.put(value, copy);
        copyFields(value, copy, classLoader, done, false);
        return copy;
    }

    private static void copyFields(Object from, Object to, ClassLoader classLoader, Map<Object, Object> done,
                                   boolean annotatedOnly) {
        Class<?> source = from.getClass();
        Class<?> target = to.getClass();
        while (source != null && target != null && source != Object.class) {
            for (Field field : source.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()
                    || (annotatedOnly && field.getAnnotations().length == 0)) {
                    continue;
                }
                try {
                    Field targetField = target.getDeclaredField(field.getName());
                    field.setAccessible(true);
                    targetField.setAccessible(true);
                    Object value = field.get(from);
                    if (annotatedOnly && value == null) {
                        continue;
                    }
                    targetField.set(to, convert(value, classLoader, done));
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new IllegalStateException("Cannot copy field " + field + " into MockClassLoader", e);
                }
            }
            source = source.getSuperclass();
            target = target.getSuperclass();
        }
    }

    private static Class<?> loadEquivalent(Class<?> type, ClassLoader classLoader) {
        if (type.isPrimitive()) {
            return type;
        }
        try {
            return Class.forName(type.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            return type;
        }
    }
}
