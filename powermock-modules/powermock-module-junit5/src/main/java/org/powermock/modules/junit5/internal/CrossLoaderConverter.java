package org.powermock.modules.junit5.internal;

import org.powermock.reflect.Whitebox;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Converts objects between the "outside" class loader (the one Jupiter uses) and PowerMock's MockClassLoader.
 * An object whose class is the same in both worlds (JDK and deferred classes, e.g. JUnit's TestInfo) is passed
 * as it is. An object of a class that exists separately in the target loader (user classes, enums, exceptions,
 * Class literals) is re-created there: enums by name, classes by name, other objects by allocating an instance
 * of the target class without calling a constructor and converting all its fields (deep, cycle safe).
 */
public class CrossLoaderConverter {

    private final ClassLoader target;
    private final Map<Object, Object> converted = new IdentityHashMap<Object, Object>();

    private CrossLoaderConverter(ClassLoader target) {
        this.target = target;
    }

    public static Object convert(Object value, ClassLoader target, Map<Object, Object> known) {
        CrossLoaderConverter converter = new CrossLoaderConverter(target);
        converter.converted.putAll(known);
        return converter.convert(value);
    }

    public static Object[] convertAll(Object[] values, ClassLoader target, Map<Object, Object> known) {
        CrossLoaderConverter converter = new CrossLoaderConverter(target);
        converter.converted.putAll(known);
        Object[] result = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = converter.convert(values[i]);
        }
        return result;
    }

    /** Copies (converted) field values of {@code from} into the existing object {@code to} of the target world. */
    public static void copyInto(Object from, Object to, ClassLoader target, Map<Object, Object> known) {
        CrossLoaderConverter converter = new CrossLoaderConverter(target);
        converter.converted.putAll(known);
        converter.converted.put(from, to);
        converter.copyFields(from.getClass(), from, to.getClass(), to);
    }

    /** The class with the same name as seen by the target loader, or null if it cannot be loaded. */
    static Class<?> counterpart(Class<?> type, ClassLoader loader) {
        if (type.isPrimitive()) {
            return type;
        }
        if (type.isArray()) {
            Class<?> component = counterpart(type.getComponentType(), loader);
            return component == null ? null : Array.newInstance(component, 0).getClass();
        }
        try {
            return Class.forName(type.getName(), false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Class) {
            Class<?> other = counterpart((Class<?>) value, target);
            return other == null ? value : other;
        }
        Class<?> type = value.getClass();
        Class<?> other = counterpart(type, target);
        if (other == null || other == type) {
            return value;
        }
        Object done = converted.get(value);
        if (done != null) {
            return done;
        }
        if (value instanceof Enum) {
            Class enumType = other.isEnum() ? other : other.getSuperclass();
            Object result = Enum.valueOf(enumType, ((Enum<?>) value).name());
            converted.put(value, result);
            return result;
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object result = Array.newInstance(other.getComponentType(), length);
            converted.put(value, result);
            for (int i = 0; i < length; i++) {
                Array.set(result, i, convert(Array.get(value, i)));
            }
            return result;
        }
        Object result = Whitebox.newInstance(other);
        converted.put(value, result);
        copyFields(type, value, other, result);
        return result;
    }

    private void copyFields(Class<?> fromType, Object from, Class<?> toType, Object to) {
        for (Class<?> f = fromType, t = toType; f != null && t != null && f != Object.class; f = f.getSuperclass(), t = t.getSuperclass()) {
            for (Field field : f.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    Field targetField = t.getDeclaredField(field.getName());
                    field.setAccessible(true);
                    targetField.setAccessible(true);
                    Object v = convert(field.get(from));
                    if (v == null || targetField.getType().isPrimitive() || targetField.getType().isInstance(v)) {
                        if (!(v == null && targetField.getType().isPrimitive())) {
                            targetField.set(to, v);
                        }
                    }
                } catch (NoSuchFieldException | RuntimeException | IllegalAccessException e) {
                    // field not accessible (e.g. JDK internals without opens): leave the default
                }
            }
        }
    }
}
