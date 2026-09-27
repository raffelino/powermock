package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Constructor;

/**
 * Maps each Jupiter test instance to its companion instance of the MockClassLoader-loaded test class, so that
 * @BeforeEach, @Test and @AfterEach of one test run on the same object.
 */
public final class MockedTestInstances {

    private MockedTestInstances() {
    }

    static Object get(ExtensionContext context, Object jupiterInstance, Class<?> mockedClass) {
        return MockClassLoaderCache.classStore(context).getOrComputeIfAbsent(
                new Key(jupiterInstance), k -> newInstance(mockedClass), Object.class);
    }

    private static Object newInstance(Class<?> mockedClass) {
        final Thread thread = Thread.currentThread();
        final ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(mockedClass.getClassLoader());
        try {
            final Constructor<?> constructor = mockedClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create an instance of " + mockedClass + " in PowerMock's MockClassLoader", e);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /** Identity key: test classes may override equals/hashCode. */
    private static final class Key {
        private final Object instance;

        Key(Object instance) {
            this.instance = instance;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && ((Key) o).instance == instance;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance);
        }
    }
}
