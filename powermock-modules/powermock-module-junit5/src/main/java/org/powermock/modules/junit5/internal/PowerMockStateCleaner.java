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
 * For @TestInstance(PER_CLASS) the state a @BeforeAll method created (spies, mocks of final classes, ...) can
 * be captured as a {@link Snapshot} and is restored after every clear, so it survives the per-test reset.
 */
public class PowerMockStateCleaner {

    public static void clear(ClassLoader mockClassLoader) {
        try {
            Class.forName(MockRepository.class.getName(), true, mockClassLoader).getMethod("clear").invoke(null);
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

    /** Copy of MockRepository's collections (the after-method runners are not part of it). */
    public static final class Snapshot {
        private final Map<Field, Object> contents = new HashMap<Field, Object>();

        public static Snapshot take() {
            Snapshot snapshot = new Snapshot();
            for (Field field : MockRepository.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || "afterMethodRunners".equals(field.getName())) {
                    continue;
                }
                Object value = read(field);
                if (value instanceof Map) {
                    List<Object[]> entries = new ArrayList<Object[]>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                        entries.add(new Object[]{entry.getKey(), entry.getValue()});
                    }
                    snapshot.contents.put(field, entries);
                } else if (value instanceof Collection) {
                    snapshot.contents.put(field, new ArrayList<Object>((Collection<?>) value));
                }
            }
            return snapshot;
        }

        @SuppressWarnings("unchecked")
        public void restore() {
            for (Map.Entry<Field, Object> entry : contents.entrySet()) {
                Object current = read(entry.getKey());
                if (current instanceof Map) {
                    for (Object[] pair : (List<Object[]>) entry.getValue()) {
                        ((Map<Object, Object>) current).put(pair[0], pair[1]);
                    }
                } else if (current instanceof Collection) {
                    ((Collection<Object>) current).addAll((List<Object>) entry.getValue());
                }
            }
        }

        private static Object read(Field field) {
            try {
                field.setAccessible(true);
                return field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
