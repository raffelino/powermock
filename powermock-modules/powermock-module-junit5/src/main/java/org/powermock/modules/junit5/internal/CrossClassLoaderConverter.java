package org.powermock.modules.junit5.internal;

import org.powermock.reflect.Whitebox;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Converts objects created outside the MockClassLoader (arguments resolved by Jupiter: parameterized-test
 * arguments, parameter resolvers ...) into equivalent objects of the classes loaded by the MockClassLoader,
 * so that they can be passed to the MockClassLoader-loaded test methods. Objects of classes that the
 * MockClassLoader shares with the outside (JDK, JUnit ...) are passed through unchanged.
 */
public class CrossClassLoaderConverter {

    private final ClassLoader classLoader;
    private final Map<Object, Object> converted = new IdentityHashMap<Object, Object>();

    public CrossClassLoaderConverter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Object[] convertAll(Object[] args) throws Exception {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = convert(args[i]);
        }
        return result;
    }

    public Object convert(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        Class<?> type = value instanceof Class ? (Class<?>) value : value.getClass();
        final Class<?> target;
        try {
            target = convertClass(type);
        } catch (ClassNotFoundException e) {
            return value; // e.g. lambdas / hidden classes: nothing equivalent in the MockClassLoader
        }
        if (value instanceof Class) {
            return target;
        }
        if (target == type && !type.isArray()) {
            return value;
        }
        Object done = converted.get(value);
        if (done != null) {
            return done;
        }
        if (value instanceof Enum) {
            Class<?> enumType = ((Enum<?>) value).getDeclaringClass();
            Object constant = convertClass(enumType).getField(((Enum<?>) value).name()).get(null);
            converted.put(value, constant);
            return constant;
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object array = Array.newInstance(target.getComponentType(), length);
            converted.put(value, array);
            for (int i = 0; i < length; i++) {
                Array.set(array, i, convert(Array.get(value, i)));
            }
            return array;
        }
        Object copy = Whitebox.newInstance(target);
        converted.put(value, copy);
        for (Class<?> c = type, t = target; c != null && c != Object.class; c = c.getSuperclass(), t = t.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Field targetField = t.getDeclaredField(field.getName());
                targetField.setAccessible(true);
                targetField.set(copy, convert(field.get(value)));
            }
        }
        return copy;
    }

    private Class<?> convertClass(Class<?> type) throws ClassNotFoundException {
        if (type.isPrimitive()) {
            return type;
        }
        if (type.isArray()) {
            return Array.newInstance(convertClass(type.getComponentType()), 0).getClass();
        }
        return Class.forName(type.getName(), false, classLoader);
    }
}
