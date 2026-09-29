package org.powermock.modules.junit5.internal;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Converts objects created by Jupiter (in the application class loader) into their counterparts in
 * PowerMock's MockClassLoader: enum constants by name, Class literals by name, arrays element-wise.
 * Objects of classes that are the same in both loaders are passed through.
 */
final class ArgumentConverter {

    private ArgumentConverter() {
    }

    static Object[] convert(ClassLoader classLoader, Object[] args) throws ClassNotFoundException {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = convert(classLoader, args[i]);
        }
        return converted;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convert(ClassLoader classLoader, Object value) throws ClassNotFoundException {
        if (value == null) {
            return null;
        }
        if (value instanceof Class) {
            Class<?> c = (Class<?>) value;
            return c.isPrimitive() ? c : loadSame(classLoader, c);
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            Class<?> target = component.isPrimitive() ? component : loadSame(classLoader, component);
            if (target == component && component.isPrimitive()) {
                return value;
            }
            int length = Array.getLength(value);
            Object copy = Array.newInstance(target, length);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, convert(classLoader, Array.get(value, i)));
            }
            return copy;
        }
        if (value instanceof Enum) {
            Class<?> enumType = ((Enum<?>) value).getDeclaringClass();
            Class target = loadSame(classLoader, enumType);
            return target == enumType ? value : Enum.valueOf(target, ((Enum<?>) value).name());
        }
        return value;
    }

    private static Class<?> loadSame(ClassLoader classLoader, Class<?> c) throws ClassNotFoundException {
        return Class.forName(c.getName(), false, classLoader);
    }

    /** Creates the shadow instance; uses a no-arg constructor or the original instance's field values. */
    static Object instantiate(Class<?> testClass, Object original, ClassLoader classLoader) throws Exception {
        Constructor<?> constructor = testClass.getDeclaredConstructors()[0];
        for (Constructor<?> c : testClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                constructor = c;
            }
        }
        constructor.setAccessible(true);
        Class<?>[] types = constructor.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = defaultValue(types[i]);
        }
        Object shadow = constructor.newInstance(args);
        if (types.length > 0) {
            copyFields(original, shadow, classLoader, false);
        }
        return shadow;
    }

    /** Copies fields that Jupiter injected into the original instance after creation (e.g. @TempDir). */
    static void copyInjectedFields(Object original, Object shadow, ClassLoader classLoader) throws Exception {
        copyFields(original, shadow, classLoader, true);
    }

    private static void copyFields(Object original, Object shadow, ClassLoader classLoader, boolean onlyInjected)
        throws Exception {
        for (Class<?> c = original.getClass(), s = shadow.getClass(); c != null && s != null && c != Object.class;
             c = c.getSuperclass(), s = s.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (onlyInjected && !isInjected(field)) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(original);
                Field target = s.getDeclaredField(field.getName());
                target.setAccessible(true);
                if (value != null && !Modifier.isStatic(target.getModifiers())) {
                    try {
                        target.set(shadow, convert(classLoader, value));
                    } catch (IllegalArgumentException ignored) {
                        // type not shared between the loaders; keep the shadow's own value
                    }
                }
            }
        }
    }

    private static boolean isInjected(Field field) {
        for (java.lang.annotation.Annotation a : field.getAnnotations()) {
            if (a.annotationType().getName().equals("org.junit.jupiter.api.io.TempDir")) {
                return true;
            }
        }
        return false;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }
}
