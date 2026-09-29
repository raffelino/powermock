package org.powermock.modules.junit5.internal;

import org.powermock.core.MockRepository;

import java.lang.reflect.InvocationTargetException;

/** Clears PowerMock's global state (MockRepository) after each test, in the MockClassLoader and outside it. */
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

    @SuppressWarnings("unchecked")
    public static void clearKeepingInstanceMocks(ClassLoader mockClassLoader) {
        java.util.Map<Object, Object> saved;
        Class<?> repo;
        try {
            repo = Class.forName(MockRepository.class.getName(), true, mockClassLoader);
            java.lang.reflect.Field f = repo.getDeclaredField("instanceMocks");
            f.setAccessible(true);
            java.util.Map<Object, Object> live = (java.util.Map<Object, Object>) f.get(null);
            saved = new java.util.IdentityHashMap<>();
            for (Object key : live.keySet()) {
                saved.put(key, live.get(key));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read PowerMock's MockRepository", e);
        }
        clear(mockClassLoader);
        try {
            for (java.lang.reflect.Method m : repo.getMethods()) {
                if (m.getName().equals("putInstanceMethodInvocationControl")) {
                    for (java.util.Map.Entry<Object, Object> e : saved.entrySet()) {
                        m.invoke(null, e.getKey(), e.getValue());
                    }
                    break;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot restore PowerMock's MockRepository", e);
        }
    }
}
