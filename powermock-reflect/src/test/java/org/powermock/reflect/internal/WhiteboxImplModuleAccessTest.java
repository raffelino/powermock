/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powermock.reflect.internal;

import org.junit.Test;
import org.powermock.reflect.exceptions.FieldNotFoundException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectPermission;
import java.security.Permission;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for the module-access check ({@code trySetAccessible}) in {@link WhiteboxImpl} and the
 * lookup / Unsafe paths that use it.
 */
public class WhiteboxImplModuleAccessTest {

    // ---------------------------------------------------------------- fixtures

    public interface OneMethodInterface {
        void doIt(String s);
    }

    /** No code, so JaCoCo adds no synthetic $jacocoInit() method to it. */
    public interface OneNoArgInterface {
        void run();
    }

    public static class OneNoArgMethod {
        private int value() {
            return 1;
        }
    }

    public static class Primitives {
        private final short s;
        private final long l;
        private final byte b;
        private final boolean z;
        private final float f;
        private final double d;
        private final char c;
        private final int i;
        private int mutableInt;

        public Primitives() {
            s = Short.parseShort("1");
            l = Long.parseLong("1");
            b = Byte.parseByte("1");
            z = Boolean.parseBoolean("false");
            f = Float.parseFloat("1");
            d = Double.parseDouble("1");
            c = "a".charAt(0);
            i = Integer.parseInt("1");
        }
    }

    public static class Statics {
        private static final int FINAL_INT = Integer.parseInt("1");
        private static int mutableStaticInt;
    }

    // --------------------------------------------- lookup methods (getAllPublicMethods etc.)

    @Test
    public void getMethodByParameterTypesAcceptsNullParameterTypes() {
        Method method = WhiteboxImpl.getMethod(OneNoArgInterface.class, (Class<?>[]) null);
        assertEquals("run", method.getName());
    }

    @Test
    public void getMethodByParameterTypesUsesPublicMethodsOfInterface() {
        Method method = WhiteboxImpl.getMethod(OneMethodInterface.class, String.class);
        assertEquals("doIt", method.getName());
    }

    @Test
    public void getMethodByNameAcceptsNullParameterTypesAndInterfaces() {
        assertEquals("value", WhiteboxImpl.getMethod(OneNoArgMethod.class, "value", (Class<?>[]) null).getName());
        assertEquals("doIt", WhiteboxImpl.getMethod(OneMethodInterface.class, "doIt", String.class).getName());
    }

    @Test
    public void getMethodsOnInterfaceUsesPublicMethods() {
        Method[] methods = WhiteboxImpl.getMethods(OneMethodInterface.class, "doIt");
        assertEquals(1, methods.length);
        assertEquals("doIt", methods[0].getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMethodsRequiresAtLeastOneName() {
        WhiteboxImpl.getMethods(OneNoArgMethod.class, (String[]) null);
    }

    @Test
    public void getAllPublicMethodsAndDoGetAllMethodsRejectNull() throws Exception {
        for (String name : new String[]{"getAllPublicMethods", "doGetAllMethods"}) {
            Method m = WhiteboxImpl.class.getDeclaredMethod(name, Class.class);
            m.setAccessible(true);
            try {
                m.invoke(null, (Object) null);
                fail(name + " should reject null");
            } catch (InvocationTargetException e) {
                assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    public void getAllMethodsOfJdkClassInUnopenedPackageDoesNotThrow() {
        // java.util.concurrent.atomic is not opened: trySetAccessible returns false on Java 9+
        assertThat(WhiteboxImpl.getAllMethods(AtomicInteger.class)).isNotEmpty();
        assertThat(WhiteboxImpl.getMethods(AtomicInteger.class, "get")).isNotEmpty();
    }

    // --------------------------------------------- field lookup

    @Test
    public void getFieldOfJdkClassInUnopenedPackageReturnsField() {
        Field field = WhiteboxImpl.getField(AtomicInteger.class, "value");
        assertEquals(int.class, field.getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setInternalStateWithWhereRejectsNullObject() {
        WhiteboxImpl.setInternalState(null, "i", 1, Primitives.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setInternalStateWithWhereRejectsNullWhere() {
        WhiteboxImpl.setInternalState(new Primitives(), "i", 1, (Class<?>) null);
    }

    @Test(expected = FieldNotFoundException.class)
    public void setInternalStateWithWhereFailsForUnknownField() {
        WhiteboxImpl.setInternalState(new Primitives(), "nope", 1, Primitives.class);
    }

    @Test
    public void setInternalStateWithWhereWrapsFailedSet() {
        try {
            WhiteboxImpl.setInternalState(new Primitives(), "mutableInt", "not an int", Primitives.class);
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Failed to set field");
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void setInternalStateByTypeWithWhereRejectsNullType() {
        WhiteboxImpl.setInternalState(new Primitives(), (Class<?>) null, 1, Primitives.class);
    }

    @Test(expected = FieldNotFoundException.class)
    public void setInternalStateByTypeWithWhereFailsForUnknownType() {
        WhiteboxImpl.setInternalState(new Primitives(), StringBuilder.class, new StringBuilder(), Primitives.class);
    }

    @Test(expected = FieldNotFoundException.class)
    public void setInternalStateByValueWithWhereDoesNotSearchHierarchy() {
        // findSingleFieldUsingStrategy with checkHierarchy=false stops at "where"
        WhiteboxImpl.setInternalState(new Primitives(), new StringBuilder(), Primitives.class);
    }

    // --------------------------------------------- Unsafe paths

    @Test
    public void setInternalStateOnInaccessibleJdkFieldFallsBackToUnsafe() {
        AtomicInteger atomic = new AtomicInteger();
        WhiteboxImpl.setInternalState(atomic, "value", 42, AtomicInteger.class);
        assertEquals(42, atomic.get());
    }

    @Test
    public void setInternalStateWritesAllPrimitiveTypesOfFinalFields() {
        Primitives p = new Primitives();
        WhiteboxImpl.setInternalState(p, "s", (short) 2);
        WhiteboxImpl.setInternalState(p, "l", 2L);
        WhiteboxImpl.setInternalState(p, "b", (byte) 2);
        WhiteboxImpl.setInternalState(p, "z", true);
        WhiteboxImpl.setInternalState(p, "f", 2f);
        WhiteboxImpl.setInternalState(p, "d", 2d);
        WhiteboxImpl.setInternalState(p, "c", 'b');
        WhiteboxImpl.setInternalState(p, "i", 2);

        assertEquals((short) 2, (short) (Short) WhiteboxImpl.getInternalState(p, "s"));
        assertEquals(2L, (long) (Long) WhiteboxImpl.getInternalState(p, "l"));
        assertEquals((byte) 2, (byte) (Byte) WhiteboxImpl.getInternalState(p, "b"));
        assertEquals(true, WhiteboxImpl.getInternalState(p, "z"));
        assertEquals(2f, (Float) WhiteboxImpl.getInternalState(p, "f"), 0f);
        assertEquals(2d, (Double) WhiteboxImpl.getInternalState(p, "d"), 0d);
        assertEquals('b', (char) (Character) WhiteboxImpl.getInternalState(p, "c"));
        assertEquals(2, (int) (Integer) WhiteboxImpl.getInternalState(p, "i"));
    }

    @Test
    public void unsafeWriteOfWrongTypeToFinalFieldIsWrapped() {
        try {
            WhiteboxImpl.setInternalState(new Primitives(), "i", "not an int");
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(ClassCastException.class);
        }
    }

    @Test
    public void unsafeWriteOfWrongTypeToStaticFinalFieldIsWrapped() {
        try {
            WhiteboxImpl.setInternalState(Statics.class, "FINAL_INT", "not an int");
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(ClassCastException.class);
        }
    }

    @Test
    public void reflectiveWriteOfWrongTypeToStaticFieldIsWrapped() {
        try {
            WhiteboxImpl.setInternalState(Statics.class, "mutableStaticInt", "not an int");
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // --------------------------------------------- trySetAccessible failure propagation
    // A SecurityManager is the only way to make AccessibleObject.trySetAccessible() throw.

    static class MarkerError extends Error {
    }

    /** Throws {@code toThrow} for suppressAccessChecks while {@code WhiteboxImpl.<method>} is on the stack. */
    static class DenyingSecurityManager extends SecurityManager {
        private final Thread thread = Thread.currentThread();
        private final String method;
        private final Throwable toThrow;

        DenyingSecurityManager(String method, Throwable toThrow) {
            this.method = method;
            this.toThrow = toThrow;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (Thread.currentThread() != thread || !(perm instanceof ReflectPermission)
                        || !"suppressAccessChecks".equals(perm.getName())) {
                return;
            }
            for (StackTraceElement e : new Throwable().getStackTrace()) {
                if (WhiteboxImpl.class.getName().equals(e.getClassName()) && method.equals(e.getMethodName())) {
                    DenyingSecurityManager.<RuntimeException>sneakyThrow(toThrow);
                }
            }
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
            throw (T) t;
        }
    }

    private static boolean isJava9OrLater() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    /** Runs {@code action} with the security manager installed and returns what it threw. */
    @SuppressWarnings("removal")
    private static Throwable thrownUnderSecurityManager(String method, Throwable toThrow, Runnable action) {
        // Java 9+ only: on Java 8 WhiteboxImpl uses setAccessible(true) directly (no trySetAccessible)
        assumeTrue(isJava9OrLater());
        SecurityManager previous = System.getSecurityManager();
        System.setSecurityManager(new DenyingSecurityManager(method, toThrow));
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        } finally {
            System.setSecurityManager(previous);
        }
    }

    @Test
    public void trySetAccessibleRethrowsRuntimeException() {
        final SecurityException denied = new SecurityException("denied");
        Throwable thrown = thrownUnderSecurityManager("getField", denied, new Runnable() {
            @Override
            public void run() {
                WhiteboxImpl.getField(Primitives.class, "i");
            }
        });
        assertSame(denied, thrown);
    }

    @Test
    public void trySetAccessibleRethrowsError() {
        final MarkerError error = new MarkerError();
        Throwable thrown = thrownUnderSecurityManager("getField", error, new Runnable() {
            @Override
            public void run() {
                WhiteboxImpl.getField(Primitives.class, "i");
            }
        });
        assertSame(error, thrown);
    }

    @Test
    public void trySetAccessibleWrapsCheckedException() {
        final Exception checked = new Exception("checked");
        Throwable thrown = thrownUnderSecurityManager("getField", checked, new Runnable() {
            @Override
            public void run() {
                WhiteboxImpl.getField(Primitives.class, "i");
            }
        });
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertSame(checked, thrown.getCause());
    }

    @Test
    public void setFieldUsingUnsafeWrapsSecurityException() {
        final SecurityException denied = new SecurityException("denied");
        Throwable thrown = thrownUnderSecurityManager("setFieldUsingUnsafe", denied, new Runnable() {
            @Override
            public void run() {
                WhiteboxImpl.setInternalState(new Primitives(), "mutableInt", 3);
            }
        });
        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertSame(denied, thrown.getCause());
    }

    @Test
    public void setStaticFieldUsingUnsafeWrapsSecurityException() {
        final SecurityException denied = new SecurityException("denied");
        Throwable thrown = thrownUnderSecurityManager("setStaticFieldUsingUnsafe", denied, new Runnable() {
            @Override
            public void run() {
                WhiteboxImpl.setInternalState(Statics.class, "mutableStaticInt", 3);
            }
        });
        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertSame(denied, thrown.getCause());
    }
}
