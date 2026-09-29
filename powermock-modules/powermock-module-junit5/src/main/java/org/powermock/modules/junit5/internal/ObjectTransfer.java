package org.powermock.modules.junit5.internal;

import org.powermock.reflect.Whitebox;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves objects between the application class loader (where Jupiter and other extensions live) and PowerMock's
 * MockClassLoader (where the test code runs).
 */
public class ObjectTransfer {

    static final Object NOT_SHARED = new Object();

    private final ClassLoader mockClassLoader;
    private final Map<Object, ShadowState> shadows;

    ObjectTransfer(ClassLoader mockClassLoader, Map<Object, ShadowState> shadows) {
        this.mockClassLoader = mockClassLoader;
        this.shadows = shadows;
    }

    /**
     * Arguments for a MockClassLoader method/constructor: test instances become their shadows, objects of classes
     * the MockClassLoader loads itself (e.g. a user class supplied by a ParameterResolver) are copied field by field
     * into an object of the MockClassLoader's class, so they behave like objects of prepared classes.
     */
    public Object[] toMockClassLoader(List<Object> arguments, Class<?>[] parameterTypes) {
        Object[] result = new Object[arguments.size()];
        for (int i = 0; i < result.length; i++) {
            Object argument = arguments.get(i);
            result[i] = parameterTypes[i].isPrimitive() || parameterTypes[i].isInstance(argument)
                ? argument
                : copy(argument, new IdentityHashMap<Object, Object>());
        }
        return result;
    }

    private Object copy(Object value, Map<Object, Object> copies) {
        if (value == null) {
            return null;
        }
        ShadowState shadow = shadows.get(value);
        if (shadow != null) {
            return shadow.getShadow();
        }
        Class<?> type = value.getClass();
        Class<?> target = load(type.getName(), mockClassLoader);
        if (target == null || target == type) {
            return value;
        }
        Object existing = copies.get(value);
        if (existing != null) {
            return existing;
        }
        if (type.isEnum() || (type.getSuperclass() != null && type.getSuperclass().isEnum())) {
            Class<?> enumType = type.isEnum() ? target : target.getSuperclass();
            return enumConstant(enumType, ((Enum<?>) value).name());
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object array = Array.newInstance(target.getComponentType(), length);
            copies.put(value, array);
            for (int i = 0; i < length; i++) {
                Array.set(array, i, copy(Array.get(value, i), copies));
            }
            return array;
        }
        Object result = Whitebox.newInstance(target);
        copies.put(value, result);
        Class<?> targetType = target;
        for (Class<?> sourceType = type; sourceType != null && targetType != null;
             sourceType = sourceType.getSuperclass(), targetType = targetType.getSuperclass()) {
            for (Field field : sourceType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    Field targetField = targetType.getDeclaredField(field.getName());
                    field.setAccessible(true);
                    targetField.setAccessible(true);
                    targetField.set(result, copy(field.get(value), copies));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot transfer " + value + " into PowerMock's MockClassLoader", e);
                }
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> enumType, String name) {
        return Enum.valueOf((Class) enumType, name);
    }

    /** The value as seen from the MockClassLoader, or {@link #NOT_SHARED} if it is an object of another class loader's class. */
    Object sharedValueInMockClassLoader(Object value) {
        if (value == null) {
            return null;
        }
        ShadowState shadow = shadows.get(value);
        if (shadow != null) {
            return shadow.getShadow();
        }
        return load(value.getClass().getName(), mockClassLoader) == value.getClass() ? value : NOT_SHARED;
    }

    /** The value as seen from the original test class's loader, or {@link #NOT_SHARED}. */
    Object sharedValueOutsideMockClassLoader(Object value, ClassLoader originalLoader) {
        if (value == null) {
            return null;
        }
        synchronized (shadows) {
            for (ShadowState state : shadows.values()) {
                if (state.getShadow() == value) {
                    return state.getOriginal();
                }
            }
        }
        return load(value.getClass().getName(), originalLoader) == value.getClass() ? value : NOT_SHARED;
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable e) {
            return null;
        }
    }
}
