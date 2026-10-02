package org.powermock.modules.junit5.internal;

import org.objenesis.ObjenesisStd;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Moves objects between the application class loader and PowerMock's MockClassLoader.
 * <p>
 * Objects whose class is the same in both loaders (JDK, Jupiter, Mockito, deferred classes) are shared as they are,
 * test instances are mapped onto their counterpart, enum constants and Class literals are looked up by name, and
 * (when allowed) other objects are deep-copied field by field into an instance of the same-named class of the
 * target loader.
 */
public final class CrossLoader {

    /** Marker for "cannot be transferred without copying". */
    static final Object NOT_SHAREABLE = new Object();

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd(true);

    private final ClassLoader target;
    private final Map<Object, Object> counterparts;
    private final boolean deepCopy;
    private final Map<Object, Object> copied = new IdentityHashMap<Object, Object>();

    /**
     * @param target       loader the result must belong to
     * @param counterparts identity map of known objects (test instances) onto their counterpart in {@code target}
     * @param deepCopy     whether objects of different classes may be deep-copied
     */
    CrossLoader(ClassLoader target, Map<Object, Object> counterparts, boolean deepCopy) {
        this.target = target;
        this.counterparts = counterparts;
        this.deepCopy = deepCopy;
    }

    /** @return the transferred value, or {@link #NOT_SHAREABLE} if it cannot be transferred */
    Object transfer(Object value) {
        if (value == null) {
            return null;
        }
        Object known = counterparts.get(value);
        if (known != null) {
            return known;
        }
        Object done = copied.get(value);
        if (done != null) {
            return done;
        }
        if (value instanceof Class) {
            Class<?> type = (Class<?>) value;
            Class<?> mapped = targetClass(type);
            return mapped == null ? value : mapped;
        }
        Class<?> type = value.getClass();
        Class<?> targetType = targetClass(type);
        if (targetType == null || targetType == type) {
            return value;
        }
        if (value instanceof Enum) {
            Class<?> enumType = ((Enum<?>) value).getDeclaringClass();
            Class<?> targetEnum = targetClass(enumType);
            return enumValue(targetEnum, ((Enum<?>) value).name());
        }
        if (!deepCopy && !(value instanceof Throwable)) {
            return NOT_SHAREABLE;
        }
        if (isGenerated(type)) {
            return NOT_SHAREABLE;
        }
        try {
            return copy(value, type, targetType);
        } catch (Exception e) {
            return NOT_SHAREABLE;
        } catch (LinkageError e) {
            return NOT_SHAREABLE;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumType, String name) {
        return Enum.valueOf((Class) enumType, name);
    }

    private Object copy(Object value, Class<?> type, Class<?> targetType) throws Exception {
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object array = Array.newInstance(targetType.getComponentType(), length);
            copied.put(value, array);
            for (int i = 0; i < length; i++) {
                Object element = transfer(Array.get(value, i));
                if (element == NOT_SHAREABLE) {
                    throw new IllegalStateException("not transferable");
                }
                Array.set(array, i, element);
            }
            return array;
        }
        Object result = OBJENESIS.newInstance(targetType);
        copied.put(value, result);
        Class<?> source = type;
        Class<?> dest = targetType;
        while (source != null && dest != null) {
            for (Field field : source.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Field destField = source == dest ? field : findField(dest, field.getName());
                if (destField == null) {
                    continue;
                }
                field.setAccessible(true);
                destField.setAccessible(true);
                Object fieldValue = transfer(field.get(value));
                if (fieldValue == NOT_SHAREABLE) {
                    continue;
                }
                destField.set(result, fieldValue);
            }
            source = source.getSuperclass();
            dest = dest.getSuperclass();
        }
        return result;
    }

    private static Field findField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private Class<?> targetClass(Class<?> type) {
        if (type.isPrimitive()) {
            return type;
        }
        try {
            return Class.forName(type.getName(), false, target);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (LinkageError e) {
            return null;
        }
    }

    private static boolean isGenerated(Class<?> type) {
        String name = type.getName();
        return name.contains("$$") || name.contains("$MockitoMock$") || name.contains("ByteBuddy")
            || name.contains("$Proxy") || type.isSynthetic();
    }

    /** Translates a throwable leaving the MockClassLoader into the application class loader's types. */
    static Throwable toApplication(Throwable throwable, ClassLoader applicationLoader, Map<Object, Object> counterparts) {
        Object result = new CrossLoader(applicationLoader, counterparts, true).transfer(throwable);
        return result instanceof Throwable ? (Throwable) result : throwable;
    }
}
