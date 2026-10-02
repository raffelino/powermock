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
            repository.getMethod("clear").invoke(null);
            if (!retainedInstanceMocks.isEmpty()) {
                Method put = null;
                for (Method m : repository.getMethods()) {
                    if (m.getName().equals("putInstanceMethodInvocationControl")) {
                        put = m;
                    }
                }
                for (Object[] entry : retainedInstanceMocks) {
                    put.invoke(null, entry[0], entry[1]);
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
        } finally {
            MockRepository.clear();
        }
    }

    static List<Object[]> instanceMocks(ClassLoader mockClassLoader) {
        try {
            Class<?> repository = Class.forName(MockRepository.class.getName(), true, mockClassLoader);
            Field field = repository.getDeclaredField("instanceMocks");
            field.setAccessible(true);
            List<Object[]> result = new ArrayList<Object[]>();
            synchronized (repository) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) field.get(null)).entrySet()) {
                    result.add(new Object[]{entry.getKey(), entry.getValue()});
                }
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read PowerMock's MockRepository", e);
        }
    }
}
