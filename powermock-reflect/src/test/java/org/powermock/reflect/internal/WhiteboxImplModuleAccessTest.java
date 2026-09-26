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
import org.powermock.reflect.Whitebox;
import org.powermock.reflect.exceptions.TooManyMethodsFoundException;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Pins down the module-access handling of {@link WhiteboxImpl} ({@code trySetAccessible}).
 * <p>
 * {@code java.util.concurrent} and {@code java.util.concurrent.atomic} are never opened to the unnamed module
 * (neither by default nor by the build's test opens), so their private members are "not opened" on Java 9+.
 * Requirement numbers (R1..R5) refer to the task specification.
 */
public class WhiteboxImplModuleAccessTest {

    private static boolean isJava9OrLater() {
        try {
            AccessibleObject.class.getMethod("trySetAccessible");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Method trySetAccessibleMethod() throws Exception {
        Method m = WhiteboxImpl.class.getDeclaredMethod("trySetAccessible", AccessibleObject.class);
        m.setAccessible(true);
        return m;
    }

    private static boolean callTrySetAccessible(Method trySetAccessible, AccessibleObject target) throws Throwable {
        try {
            return (Boolean) trySetAccessible.invoke(null, target);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Method declaredMethod(Method[] methods, Class<?> declaringClass, String name) {
        for (Method m : methods) {
            if (m.getDeclaringClass() == declaringClass && m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- R1 / R4: detection

    @Test
    public void findTrySetAccessibleReturnsTheJdkMethodOnJava9AndNullOnJava8() throws Exception {  // R1 + R4
        Method found = Whitebox.invokeMethod(WhiteboxImpl.class, "findTrySetAccessible");
        if (isJava9OrLater()) {
            assertEquals(AccessibleObject.class.getMethod("trySetAccessible"), found);
        } else {
            assertNull(found);
        }
    }

    // ---------------------------------------------------------------- R1: JDK members in non-opened packages

    @Test
    public void trySetAccessibleReturnsFalseForPrivateJdkMethodInNonOpenedPackageOnJava9() throws Throwable {  // R1
        assumeTrue(isJava9OrLater());
        Method initTable = ConcurrentHashMap.class.getDeclaredMethod("initTable");
        assertFalse(callTrySetAccessible(trySetAccessibleMethod(), initTable));
        assertFalse(initTable.isAccessible());
    }

    @Test
    public void getAllMethodsOfJdkClassInNonOpenedPackageDoesNotThrowAndLeavesPrivateMethodsInaccessible() {  // R1 + R4
        Method[] methods = WhiteboxImpl.getAllMethods(ConcurrentHashMap.class);
        Method initTable = declaredMethod(methods, ConcurrentHashMap.class, "initTable");
        assertNotNull(initTable);
        // Java 8: setAccessible(true) as before; Java 9+: stays inaccessible instead of throwing
        assertEquals(!isJava9OrLater(), initTable.isAccessible());
    }

    @Test
    public void getMethodsByNameOnJdkClassInNonOpenedPackageReturnsInaccessibleMethod() {  // R1 + R3
        Method[] methods = WhiteboxImpl.getMethods(ConcurrentHashMap.class, "initTable");
        assertThat(methods).hasSize(1);
        assertEquals("initTable", methods[0].getName());
        assertEquals(!isJava9OrLater(), methods[0].isAccessible());
    }

    @Test
    public void getMethodsOnJdkInterfaceUsesPublicMethodsWithoutThrowing() {  // R1 (getAllPublicMethods path)
        Method[] methods = WhiteboxImpl.getMethods(ConcurrentMap.class, "putIfAbsent");
        assertThat(methods).isNotEmpty();
        for (Method m : methods) {
            assertEquals("putIfAbsent", m.getName());
        }
    }

    @Test
    public void getMethodByParameterTypesOnJdkInterfaceDoesNotThrow() throws Exception {  // R1 (getMethod(Class, Class...) + getAllPublicMethods)
        // ConcurrentMap declares exactly one method taking (Object, Object, Object): replace(K, V, V)
        Method method = WhiteboxImpl.getMethod(ConcurrentMap.class, Object.class, Object.class, Object.class);
        assertEquals(ConcurrentMap.class.getMethod("replace", Object.class, Object.class, Object.class), method);
    }

    @Test
    public void getFieldOnJdkClassInNonOpenedPackageReturnsFieldWithoutThrowing() {  // R1 (getField)
        Field sizeCtl = WhiteboxImpl.getField(ConcurrentHashMap.class, "sizeCtl");
        assertEquals("sizeCtl", sizeCtl.getName());
        assertEquals(!isJava9OrLater(), sizeCtl.isAccessible());
    }

    @Test
    public void setInternalStateByNameOnJdkObjectInNonOpenedPackageSetsValue() {  // R1 (findSingleFieldUsingStrategy + setFieldUsingUnsafe)
        AtomicInteger atomic = new AtomicInteger(1);
        WhiteboxImpl.setInternalState(atomic, "value", 42);
        assertEquals(42, atomic.get());
    }

    @Test
    public void setInternalStateByNameAndWhereOnJdkObjectInNonOpenedPackageSetsValue() {  // R1 (setInternalState(.., where) + getField(String, Class))
        AtomicInteger atomic = new AtomicInteger(1);
        WhiteboxImpl.setInternalState(atomic, "value", 43, AtomicInteger.class);
        assertEquals(43, atomic.get());
    }

    @Test
    public void setInternalStateByTypeOnJdkObjectInNonOpenedPackageSetsValue() {  // R1 (findFieldOrThrowException)
        AtomicInteger atomic = new AtomicInteger(1);
        WhiteboxImpl.setInternalState(atomic, int.class, 44, AtomicInteger.class);
        assertEquals(44, atomic.get());
    }

    // ---------------------------------------------------------------- R2: user classes unchanged

    @SuppressWarnings("unused")
    static class UserClass {
        private static String staticField = "static";
        private static final Integer STATIC_FINAL_FIELD = Integer.valueOf(1);
        private final String finalField = new String("final");
        private int counter = 1;

        private String secret(String in) {
            return "secret:" + in;
        }

        private String secret(int in) {
            return "secretInt:" + in;
        }
    }

    @Test
    public void privateMethodsOfUserClassAreMadeAccessibleAndInvokable() throws Exception {  // R2
        Method[] methods = WhiteboxImpl.getMethods(UserClass.class, "secret");
        assertThat(methods).hasSize(2);
        for (Method m : methods) {
            assertTrue(m.isAccessible());
        }
        UserClass instance = new UserClass();
        assertEquals("secret:x", Whitebox.invokeMethod(instance, "secret", "x"));
        assertEquals("secretInt:3", Whitebox.invokeMethod(instance, "secret", 3));
    }

    @Test
    public void allMethodsOfUserClassAreMadeAccessible() {  // R2
        for (Method m : WhiteboxImpl.getAllMethods(UserClass.class)) {
            if (m.getDeclaringClass() == UserClass.class) {
                assertTrue(m + " should be accessible", m.isAccessible());
            }
        }
    }

    @Test
    public void privateFieldsOfUserClassAreMadeAccessibleAndReadable() throws Exception {  // R2
        Field field = WhiteboxImpl.getField(UserClass.class, "counter");
        assertTrue(field.isAccessible());
        UserClass instance = new UserClass();
        assertEquals(1, field.get(instance));
        assertEquals(Integer.valueOf(1), Whitebox.getInternalState(instance, "counter"));
    }

    @Test
    public void setInternalStateOnUserClassFieldsStillWorks() {  // R2 (setFieldUsingUnsafe / setStaticFieldUsingUnsafe)
        UserClass instance = new UserClass();
        Whitebox.setInternalState(instance, "counter", 5);
        Whitebox.setInternalState(instance, "finalField", "changed");
        assertEquals(Integer.valueOf(5), Whitebox.getInternalState(instance, "counter"));
        assertEquals("changed", Whitebox.getInternalState(instance, "finalField"));

        String oldStatic = UserClass.staticField;
        Integer oldStaticFinal = Whitebox.getInternalState(UserClass.class, "STATIC_FINAL_FIELD");
        try {
            Whitebox.setInternalState(UserClass.class, "staticField", "newStatic");
            Whitebox.setInternalState(UserClass.class, "STATIC_FINAL_FIELD", Integer.valueOf(2));
            assertEquals("newStatic", Whitebox.getInternalState(UserClass.class, "staticField"));
            assertEquals(Integer.valueOf(2), Whitebox.getInternalState(UserClass.class, "STATIC_FINAL_FIELD"));
        } finally {
            Whitebox.setInternalState(UserClass.class, "staticField", oldStatic);
            Whitebox.setInternalState(UserClass.class, "STATIC_FINAL_FIELD", oldStaticFinal);
        }
    }

    @Test
    public void trySetAccessibleReturnsTrueForPrivateUserMethod() throws Throwable {  // R2
        Method secret = UserClass.class.getDeclaredMethod("secret", String.class);
        assertTrue(callTrySetAccessible(trySetAccessibleMethod(), secret));
        assertTrue(secret.isAccessible());
    }

    // ---------------------------------------------------------------- R3: keep, not drop

    @Test
    public void getAllMethodsKeepsEveryDeclaredMethodOfJdkClassEvenIfInaccessible() {  // R3
        Set<Method> all = new HashSet<Method>(Arrays.asList(WhiteboxImpl.getAllMethods(ConcurrentHashMap.class)));
        List<Method> missing = new ArrayList<Method>();
        for (Method declared : ConcurrentHashMap.class.getDeclaredMethods()) {
            if (!"finalize".equals(declared.getName()) && !all.contains(declared)) {
                missing.add(declared);
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    public void getMethodsWithSeveralNamesKeepsInaccessibleJdkMethods() {  // R3
        Method[] methods = WhiteboxImpl.getMethods(ConcurrentHashMap.class, "initTable", "tabAt");
        Set<String> names = new HashSet<String>();
        for (Method m : methods) {
            names.add(m.getName());
        }
        assertEquals(new HashSet<String>(Arrays.asList("initTable", "tabAt")), names);
    }

    @Test
    public void getMethodByParameterTypesStillReportsTooManyMethodsOnJdkClass() {  // R3 (+R1)
        // get, containsKey, containsValue, remove, contains, equals all take a single Object
        try {
            WhiteboxImpl.getMethod(ConcurrentHashMap.class, Object.class);
            fail("Expected TooManyMethodsFoundException");
        } catch (TooManyMethodsFoundException expected) {
            assertThat(expected.getMessage()).contains("get").contains("containsKey");
        }
    }

    @Test
    public void overloadResolutionOnJdkClassSeesAllCandidates() throws Exception {  // R3
        Method best = WhiteboxImpl.getBestMethodCandidate(ConcurrentHashMap.class, "remove",
                new Class<?>[]{Object.class, Object.class}, false);
        assertEquals(ConcurrentHashMap.class.getMethod("remove", Object.class, Object.class), best);
        best = WhiteboxImpl.getBestMethodCandidate(ConcurrentHashMap.class, "remove",
                new Class<?>[]{Object.class}, false);
        assertEquals(ConcurrentHashMap.class.getMethod("remove", Object.class), best);
    }

    // ---------------------------------------------------------------- R4: Java 8 unchanged

    @Test
    public void onJava8SetAccessibleTrueIsCalledOnTheObject() throws Throwable {  // R4
        final List<Boolean> calls = new ArrayList<Boolean>();
        @SuppressWarnings("deprecation")
        AccessibleObject recording = new AccessibleObject() {
            @Override
            public void setAccessible(boolean flag) {
                calls.add(flag);
                super.setAccessible(flag);
            }
        };
        assertTrue(callTrySetAccessible(trySetAccessibleMethod(), recording));
        assertTrue(recording.isAccessible());
        if (!isJava9OrLater()) {
            assertEquals(Arrays.asList(Boolean.TRUE), calls);
        }
    }

    @Test
    public void onJava8EveryMethodOfJdkClassIsMadeAccessible() {  // R4 (on Java 9+ this pins R1 instead)
        for (Method m : WhiteboxImpl.getAllMethods(AtomicInteger.class)) {
            if (m.getDeclaringClass() == AtomicInteger.class && !isJava9OrLater()) {
                assertTrue(m + " should be accessible on Java 8", m.isAccessible());
            }
        }
        if (isJava9OrLater()) {
            Field value = WhiteboxImpl.getField(AtomicInteger.class, "value");
            assertFalse(value.isAccessible());
        }
    }

    // ---------------------------------------------------------------- R5: no exception is swallowed

    /** Denies {@code suppressAccessChecks} requested via WhiteboxImpl.trySetAccessible on the installing thread. */
    private static final class DenyingSecurityManager extends SecurityManager {
        private final Thread thread = Thread.currentThread();
        private final RuntimeException runtimeException;
        private final Error error;

        DenyingSecurityManager(RuntimeException runtimeException, Error error) {
            this.runtimeException = runtimeException;
            this.error = error;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (Thread.currentThread() == thread && perm instanceof ReflectPermission
                    && "suppressAccessChecks".equals(perm.getName()) && calledFromWhiteboxTrySetAccessible()) {
                if (error != null) {
                    throw error;
                }
                throw runtimeException;
            }
        }

        // Only fail the access check made by WhiteboxImpl.trySetAccessible, not unrelated JDK-internal
        // setAccessible calls (e.g. annotation proxies) that may happen on the same thread.
        private static boolean calledFromWhiteboxTrySetAccessible() {
            for (StackTraceElement e : new Throwable().getStackTrace()) {
                if (WhiteboxImpl.class.getName().equals(e.getClassName()) && "trySetAccessible".equals(e.getMethodName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
        }
    }

    private interface Action {
        void run() throws Throwable;
    }

    private static Throwable thrownWithSecurityManager(DenyingSecurityManager sm, Action action) {
        // Warm up: the first reflective call of AccessibleObject.trySetAccessible (Java 9+) creates a method
        // accessor, which itself calls setAccessible on JDK internals; that must not happen under the test manager.
        WhiteboxImpl.getField(UserClass.class, "counter");
        SecurityManager previous = System.getSecurityManager();
        System.setSecurityManager(sm);
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        } finally {
            System.setSecurityManager(previous);
        }
    }

    private static final SecurityException DENIED = new SecurityException("denied by test");

    private static final class TestError extends Error {
        TestError() {
            super("error from test");
        }
    }

    // Separate classes per test: getAllMethods caches its result, the cache must be cold.
    @SuppressWarnings("unused")
    static class SecurityTarget1 { private void hidden() { } }
    @SuppressWarnings("unused")
    static class SecurityTarget2 { private void hidden() { } }
    @SuppressWarnings("unused")
    static class SecurityTarget3 { private int hidden; }
    @SuppressWarnings("unused")
    static class SecurityTarget4 { private int hidden; }
    @SuppressWarnings("unused")
    static class SecurityTarget5 { private int hidden; }
    @SuppressWarnings("unused")
    static class SecurityTarget6 { private void hidden(String s) { } }

    @Test
    public void securityExceptionFromTrySetAccessiblePropagates() throws Exception {  // R5
        final Method trySetAccessible = trySetAccessibleMethod();
        final Method target = SecurityTarget6.class.getDeclaredMethod("hidden", String.class);
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(DENIED, null), new Action() {
            @Override
            public void run() throws Throwable {
                callTrySetAccessible(trySetAccessible, target);
            }
        });
        assertSame(DENIED, thrown);
    }

    @Test
    public void errorFromTrySetAccessiblePropagates() throws Exception {  // R5
        final Method trySetAccessible = trySetAccessibleMethod();
        final Method target = SecurityTarget6.class.getDeclaredMethod("hidden", String.class);
        final TestError error = new TestError();
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(null, error), new Action() {
            @Override
            public void run() throws Throwable {
                callTrySetAccessible(trySetAccessible, target);
            }
        });
        assertSame(error, thrown);
    }

    @Test
    public void securityExceptionPropagatesFromGetMethods() {  // R5 (doGetAllMethods)
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(DENIED, null), new Action() {
            @Override
            public void run() {
                WhiteboxImpl.getMethods(SecurityTarget1.class, "hidden");
            }
        });
        assertSame(DENIED, thrown);
    }

    @Test
    public void errorPropagatesFromGetAllMethods() {  // R5 (doGetAllMethods)
        final TestError error = new TestError();
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(null, error), new Action() {
            @Override
            public void run() {
                WhiteboxImpl.getAllMethods(SecurityTarget2.class);
            }
        });
        assertSame(error, thrown);
    }

    @Test
    public void securityExceptionPropagatesFromGetField() {  // R5 (getField)
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(DENIED, null), new Action() {
            @Override
            public void run() {
                WhiteboxImpl.getField(SecurityTarget3.class, "hidden");
            }
        });
        assertSame(DENIED, thrown);
    }

    @Test
    public void securityExceptionPropagatesFromSetInternalStateByName() {  // R5 (findSingleFieldUsingStrategy)
        final SecurityTarget4 instance = new SecurityTarget4();
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(DENIED, null), new Action() {
            @Override
            public void run() {
                WhiteboxImpl.setInternalState(instance, "hidden", 1);
            }
        });
        assertSame(DENIED, thrown);
    }

    @Test
    public void securityExceptionPropagatesFromSetInternalStateByType() {  // R5 (findFieldOrThrowException)
        final SecurityTarget5 instance = new SecurityTarget5();
        Throwable thrown = thrownWithSecurityManager(new DenyingSecurityManager(DENIED, null), new Action() {
            @Override
            public void run() {
                WhiteboxImpl.setInternalState(instance, int.class, 1, SecurityTarget5.class);
            }
        });
        assertSame(DENIED, thrown);
    }
}
