package org.powermock.modules.junit5.internal;

import org.objenesis.ObjenesisStd;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Converts objects created outside the MockClassLoader (Jupiter-resolved parameters, constructor
 * arguments) into equivalent objects of the MockClassLoader's classes: enum constants by name, Class
 * literals by name, other objects by allocating the target class and copying fields recursively.
 */
public class CrossLoaderConverter {

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd(true);

    private final ClassLoader classLoader;
    private final Map<Object, Object> converted = new IdentityHashMap<Object, Object>();

    public CrossLoaderConverter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public CrossLoaderConverter seed(Object original, Object replacement) {
        converted.put(original, replacement);
        return this;
    }

    public Object convert(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (converted.containsKey(value)) {
            return converted.get(value);
        }
        Class<?> type = value.getClass();
        if (value instanceof Class) {
            Class<?> c = (Class<?>) value;
            return c.isPrimitive() || c.isArray() ? c : loadOrSame(c);
        }
        Class<?> target = type.isArray() ? null : loadOrSame(type);
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            if (component.isPrimitive()) {
                return value;
            }
            int length = Array.getLength(value);
            Class<?> targetComponent = loadOrSame(component);
            Object copy = Array.newInstance(targetComponent, length);
            converted.put(value, copy);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, convert(Array.get(value, i)));
            }
            return targetComponent == component && sameElements(value, copy) ? value : copy;
        }
        if (target == type) {
            return value;
        }
        if (type.isEnum() || (type.getSuperclass() != null && type.getSuperclass().isEnum())) {
            Enum<?> e = (Enum<?>) value;
            Class<?> enumType = loadOrSame(e.getDeclaringClass());
            for (Object constant : enumType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(e.name())) {
                    return constant;
                }
            }
            return value;
        }
        Object copy = OBJENESIS.newInstance(target);
        converted.put(value, copy);
        copyFields(value, copy);
        return copy;
    }

    public void copyFields(Object from, Object to) throws Exception {
        Class<?> fromClass = from.getClass();
        Class<?> toClass = to.getClass();
        while (fromClass != null && fromClass != Object.class && toClass != null) {
            for (Field field : fromClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Field target;
                try {
                    target = toClass.getDeclaredField(field.getName());
                } catch (NoSuchFieldException e) {
                    continue;
                }
                field.setAccessible(true);
                target.setAccessible(true);
                Object value = field.get(from);
                target.set(to, field.getType().isPrimitive() ? value : convert(value));
            }
            fromClass = fromClass.getSuperclass();
            toClass = toClass.getSuperclass();
        }
    }

    private static boolean sameElements(Object a, Object b) {
        for (int i = 0; i < Array.getLength(a); i++) {
            if (Array.get(a, i) != Array.get(b, i)) {
                return false;
            }
        }
        return true;
    }

    private Class<?> loadOrSame(Class<?> c) {
        try {
            return Class.forName(c.getName(), false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            return c;
        }
    }
}
