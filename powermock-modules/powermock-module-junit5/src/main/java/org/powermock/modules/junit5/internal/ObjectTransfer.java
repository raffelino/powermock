package org.powermock.modules.junit5.internal;

import org.powermock.reflect.Whitebox;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Moves objects across the class-loader boundary: an object whose class is loaded differently by the
 * target class loader (a user class loaded by both the application class loader and the MockClassLoader)
 * is copied into an instance of the target loader's class of the same name (enum constants by name,
 * Class literals by name, arrays element-wise, other objects field by field without calling a constructor).
 * Objects of classes shared by both loaders (JDK, JUnit, PowerMock core) are passed as they are.
 */
public final class ObjectTransfer {

    /** Objects that already have a counterpart in the target loader (e.g. test instance -> shadow instance). */
    public interface KnownCounterparts {
        Object counterpartOf(Object source);
    }

    private final ClassLoader target;
    private final KnownCounterparts known;
    private final Map<Object, Object> copies = new IdentityHashMap<Object, Object>();

    private ObjectTransfer(ClassLoader target, KnownCounterparts known) {
        this.target = target;
        this.known = known;
    }

    public static Object transfer(Object value, ClassLoader target, KnownCounterparts known) {
        return new ObjectTransfer(target, known).convert(value);
    }

    public static Object transfer(Object value, ClassLoader target) {
        return transfer(value, target, null);
    }

    /** Converts an exception leaving the MockClassLoader; falls back to the original if it cannot be copied. */
    public static Throwable transferThrowable(Throwable t, ClassLoader target) {
        try {
            Object converted = transfer(t, target);
            return converted instanceof Throwable ? (Throwable) converted : t;
        } catch (RuntimeException e) {
            return t;
        } catch (LinkageError e) {
            return t;
        }
    }

    public static Class<?> targetClass(Class<?> type, ClassLoader target) {
        if (type.isPrimitive()) {
            return type;
        }
        try {
            Class<?> result = Class.forName(type.getName(), false, target);
            return result;
        } catch (ClassNotFoundException e) {
            return type;
        } catch (LinkageError e) {
            return type;
        }
    }

    private Object convert(Object value) {
        if (value == null) {
            return null;
        }
        if (known != null) {
            Object counterpart = known.counterpartOf(value);
            if (counterpart != null) {
                return counterpart;
            }
        }
        if (value instanceof Class) {
            return targetClass((Class<?>) value, target);
        }
        Class<?> sourceClass = value.getClass();
        if (sourceClass.isArray()) {
            return convertArray(value);
        }
        Class<?> targetClass = targetClass(sourceClass, target);
        if (targetClass == sourceClass) {
            return value;
        }
        Object existing = copies.get(value);
        if (existing != null) {
            return existing;
        }
        if (value instanceof Enum) {
            Object constant = enumConstant(targetClass, ((Enum<?>) value).name());
            copies.put(value, constant);
            return constant;
        }
        Object copy = Whitebox.newInstance(targetClass);
        copies.put(value, copy);
        copyFields(value, sourceClass, copy);
        return copy;
    }

    private Object convertArray(Object array) {
        Class<?> component = array.getClass().getComponentType();
        Class<?> targetComponent = targetClass(component, target);
        if (component.isPrimitive()) {
            return array;
        }
        int length = Array.getLength(array);
        boolean changed = targetComponent != component;
        Object[] converted = new Object[length];
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            converted[i] = convert(element);
            changed |= converted[i] != element;
        }
        if (!changed) {
            return array;
        }
        Object result = Array.newInstance(targetComponent, length);
        for (int i = 0; i < length; i++) {
            Array.set(result, i, converted[i]);
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> enumClass, String name) {
        Class<?> type = enumClass;
        while (type != null && !type.isEnum()) {
            type = type.getSuperclass(); // constant with a body: its class is an anonymous subclass
        }
        return Enum.valueOf((Class) type, name);
    }

    private void copyFields(Object source, Class<?> sourceClass, Object copy) {
        Class<?> sourceType = sourceClass;
        Class<?> targetType = copy.getClass();
        while (sourceType != null && targetType != null && sourceType != Object.class) {
            for (Field field : sourceType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    Field targetField = targetType.getDeclaredField(field.getName());
                    field.setAccessible(true);
                    targetField.setAccessible(true);
                    Object value = convert(field.get(source));
                    if (value == null || boxed(targetField.getType()).isInstance(value)) {
                        targetField.set(copy, value);
                    }
                } catch (NoSuchFieldException e) {
                    // class changed shape between loaders (e.g. instrumentation), leave the default
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            sourceType = sourceType.getSuperclass();
            targetType = targetType.getSuperclass();
        }
    }

    static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        return Void.class;
    }
}
