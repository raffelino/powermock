package org.powermock.modules.junit5.internal;

import org.powermock.core.MockRepository;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Clears PowerMock's global state (MockRepository) after each test, in the MockClassLoader and outside it. */
public class PowerMockStateCleaner {

    public static void clear(ClassLoader mockClassLoader) {
        clear(mockClassLoader, new ArrayList<Object[]>());
    }

    /**
     * Clears the MockClassLoader's MockRepository, then re-registers the given instance mocks (PER_CLASS state
     * created in @BeforeAll that must outlive the per-test reset).
     */
    static void clear(ClassLoader mockClassLoader, List<Object[]> retainedInstanceMocks) {
        try {
            Class<?> repository = Class.forName(MockRepository.class.getName(), true, mockClassLoader);
            try {
                repository.getMethod("clear").invoke(null);
            } finally {
                MockRepository.clear();
            }
            // Mockito's plugins (PowerMockMaker) may use either loader's MockRepository: restore in the one the
            // control came from.
            for (Object[] entry : retainedInstanceMocks) {
                Class<?> target = (Class<?>) entry[2];
                for (Method m : target.getMethods()) {
                    if (m.getName().equals("putInstanceMethodInvocationControl")) {
                        m.invoke(null, entry[0], entry[1]);
                    }
                }
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
        }
    }

    static List<Object[]> instanceMocks(ClassLoader mockClassLoader) {
        try {
            List<Object[]> result = new ArrayList<Object[]>();
            Class<?> repository = Class.forName(MockRepository.class.getName(), true, mockClassLoader);
            collect(repository, result);
            if (repository != MockRepository.class) {
                collect(MockRepository.class, result);
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read PowerMock's MockRepository", e);
        }
    }

    private static void collect(Class<?> repository, List<Object[]> result) throws ReflectiveOperationException {
        Field field = repository.getDeclaredField("instanceMocks");
        field.setAccessible(true);
        synchronized (repository) {
            Object map = field.get(null);
            // ListMap: read its entry list directly (its keySet() hashes the mocks)
            Field entriesField = map.getClass().getDeclaredField("entries");
            entriesField.setAccessible(true);
            for (Object entry : (List<?>) entriesField.get(map)) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
                result.add(new Object[]{e.getKey(), e.getValue(), repository});
            }
        }
    }
}
