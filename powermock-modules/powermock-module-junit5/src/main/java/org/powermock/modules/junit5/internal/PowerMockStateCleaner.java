package org.powermock.modules.junit5.internal;

import org.powermock.core.MockRepository;

/** Clears PowerMock's global state (MockRepository) after each test, in the MockClassLoader and outside it. */
public class PowerMockStateCleaner {

    public static void clear(ClassLoader mockClassLoader) {
        try {
            Class.forName(MockRepository.class.getName(), true, mockClassLoader).getMethod("clear").invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot clear PowerMock's MockRepository", e);
        }
        MockRepository.clear();
    }
}
