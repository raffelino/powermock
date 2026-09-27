package org.powermock.modules.junit5.internal;

import org.powermock.core.MockRepository;
import org.powermock.reflect.Whitebox;

/** Clears PowerMock's per-test state (MockRepository) as seen from the given MockClassLoader. */
public final class MockRepositoryCleaner {

    private MockRepositoryCleaner() {
    }

    public static void clear(ClassLoader mockLoader) throws Exception {
        final Class<?> mockRepository = Class.forName(MockRepository.class.getName(), true, mockLoader);
        Whitebox.invokeMethod(mockRepository, "clear");
    }
}
