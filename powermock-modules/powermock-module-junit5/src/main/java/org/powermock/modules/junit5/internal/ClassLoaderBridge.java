package org.powermock.modules.junit5.internal;

import org.powermock.reflect.Whitebox;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Carries objects created outside the MockClassLoader (parameters resolved by Jupiter or other extensions,
 * values extensions put into test instance fields) into the MockClassLoader, so that the test code sees
 * objects of its own (possibly prepared) classes: Class literals and enum constants are looked up by name,
 * other objects of classes the MockClassLoader loads itself are copied field by field.
 */
public final class ClassLoaderBridge {

    private ClassLoaderBridge() {
    }

    public static Object toLoader(Object value, ClassLoader loader) {
        return convert(value, loader, new IdentityHashMap<Object, Object>());
    }

    private static Object convert(Object value, ClassLoader loader, Map<Object, Object> converted) {
        if (value == null) {
            return null;
        }
        if (value instanceof Class) {
            Class<?> type = (Class<?>) value;
            Class<?> target = loadOrNull(type, loader);
            return target == null ? type : target;
        }
        Class<?> type = value.getClass();
        if (type.getClassLoader() == null && !type.isArray()) {
            return value; // JDK classes are shared by all loaders
        }
        Object done = converted.get(value);
        if (done != null) {
            return done;
        }
        if (type.isArray()) {
            Class<?> componentTarget = type.getComponentType().isPrimitive() ? null : loadOrNull(type.getComponentType(), loader);
            if (componentTarget == null || componentTarget == type.getComponentType()) {
                return value;
            }
            int length = Array.getLength(value);
            Object copy = Array.newInstance(componentTarget, length);
            converted.put(value, copy);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, convert(Array.get(value, i), loader, converted));
            }
            return copy;
        }
        Class<?> target = loadOrNull(type, loader);
        if (target == null || target == type) {
            return value;
        }
        if (value instanceof Enum) {
            Class<?> enumType = ((Enum<?>) value).getDeclaringClass();
            Class<?> targetEnum = loadOrNull(enumType, loader);
            for (Object constant : targetEnum.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(((Enum<?>) value).name())) {
                    converted.put(value, constant);
                    return constant;
                }
            }
            throw new IllegalStateException("No enum constant " + value + " in " + targetEnum);
        }
        Object copy = Whitebox.newInstance(target);
        converted.put(value, copy);
        for (Class<?> c = type, t = target; c != null && t != null && c != Object.class; c = c.getSuperclass(), t = t.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Field targetField = t.getDeclaredField(field.getName());
                    targetField.setAccessible(true);
                    targetField.set(copy, convert(field.get(value), loader, converted));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot copy field " + field + " into " + loader, e);
                }
            }
        }
        return copy;
    }

    private static Class<?> loadOrNull(Class<?> type, ClassLoader loader) {
        if (type.isPrimitive()) {
            return type;
        }
        try {
            return Class.forName(type.getName(), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
