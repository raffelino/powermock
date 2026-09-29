package org.powermock.modules.junit5.internal;

import org.powermock.core.MockRepository;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clears PowerMock's global state (MockRepository) after each test, in the MockClassLoader and outside it.
 * State created in {@code @BeforeAll} (e.g. a PER_CLASS spy of a prepared class) can be captured with
 * {@link #snapshot(ClassLoader)} and is then restored after every clear.
 */
public class PowerMockStateCleaner {

    /** MockRepository fields not captured: runners are one-shot, static initialiser suppression is never cleared. */
    private static final List<String> NOT_CAPTURED = java.util.Arrays.asList("afterMethodRunners", "suppressStaticInitializers");

    public static void clear(ClassLoader mockClassLoader) {
        clear(mockClassLoader, null);
    }

    public static void clear(ClassLoader mockClassLoader, Map<String, Object> keep) {
        try {
            Class<?> repository = Class.forName(MockRepository.class.getName(), true, mockClassLoader);
            repository.getMethod("clear").invoke(null);
            if (keep != null) {
                restore(repository, keep);
            }
        } catch (InvocationTargetException e) {
            // e.g. Mockito's UnfinishedStubbingException from the after-method runner: report the real cause
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Cannot clear PowerMock's MockRepository", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot clear PowerMock's MockRepository", e);
        } finally {
            MockRepository.clear();
        }
    }

    /** A copy of the MockClassLoader's MockRepository content (per field: map entries or collection elements). */
    public static Map<String, Object> snapshot(ClassLoader mockClassLoader) {
        Map<String, Object> state = new HashMap<String, Object>();
        try {
            Class<?> repository = Class.forName(MockRepository.class.getName(), true, mockClassLoader);
            synchronized (repository) {
                for (Field field : repository.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) || NOT_CAPTURED.contains(field.getName())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof Map) {
                        // keySet/get only: MockRepository's ListMap (identity keys) does not support entrySet()
                        Map<?, ?> map = (Map<?, ?>) value;
                        List<Map.Entry<Object, Object>> entries = new ArrayList<Map.Entry<Object, Object>>();
                        for (Object key : new ArrayList<Object>(map.keySet())) {
                            entries.add(new java.util.AbstractMap.SimpleEntry<Object, Object>(key, map.get(key)));
                        }
                        state.put(field.getName(), entries);
                    } else if (value instanceof Collection) {
                        state.put(field.getName(), new ArrayList<Object>((Collection<?>) value));
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read PowerMock's MockRepository", e);
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static void restore(Class<?> repository, Map<String, Object> state) throws ReflectiveOperationException {
        synchronized (repository) {
            for (Map.Entry<String, Object> e : state.entrySet()) {
                Field field = repository.getDeclaredField(e.getKey());
                field.setAccessible(true);
                Object target = field.get(null);
                if (target instanceof Map) {
                    for (Map.Entry<Object, Object> entry : (List<Map.Entry<Object, Object>>) e.getValue()) {
                        ((Map<Object, Object>) target).put(entry.getKey(), entry.getValue());
                    }
                } else if (target instanceof Collection) {
                    ((Collection<Object>) target).addAll((List<Object>) e.getValue());
                }
            }
        }
    }
}
