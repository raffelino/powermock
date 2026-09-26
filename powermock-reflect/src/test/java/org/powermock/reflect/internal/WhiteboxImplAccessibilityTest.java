package org.powermock.reflect.internal;

import org.junit.Test;
import org.powermock.reflect.exceptions.FieldNotFoundException;
import org.powermock.reflect.exceptions.MethodNotFoundException;
import org.powermock.reflect.exceptions.TooManyFieldsFoundException;
import org.powermock.reflect.exceptions.TooManyMethodsFoundException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Covers the module-access check ({@code trySetAccessible}) of {@link WhiteboxImpl} and the lookup and
 * Unsafe-based setter paths that use it. Runs on Java 8 and on Java 9+ (only java.lang, java.util,
 * java.lang.reflect and java.awt.font opened); {@code java.io} is deliberately used as a non-opened package.
 */
public class WhiteboxImplAccessibilityTest {

    private static boolean isJava9OrLater() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    private static Object invokePrivateStatic(String name, Class<?>[] types, Object... args) throws Throwable {
        Method m = WhiteboxImpl.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static boolean trySetAccessible(AccessibleObject o) throws Throwable {
        return (Boolean) invokePrivateStatic("trySetAccessible", new Class<?>[]{AccessibleObject.class}, o);
    }

    // ---- trySetAccessible / findTrySetAccessible ----

    @Test
    public void findTrySetAccessibleReturnsMethodOnlyOnJava9OrLater() throws Throwable {
        Method found = (Method) invokePrivateStatic("findTrySetAccessible", new Class<?>[0]);
        if (isJava9OrLater()) {
            assertNotNull(found);
            assertEquals("trySetAccessible", found.getName());
        } else {
            assertNull(found);
        }
    }

    @Test
    public void trySetAccessibleMakesOwnPrivateFieldAccessible() throws Throwable {
        Field field = Holder.class.getDeclaredField("plainInt");
        assertTrue(trySetAccessible(field));
        assertTrue(field.isAccessible());
    }

    @Test
    public void trySetAccessibleReturnsFalseForFieldInNonOpenedJdkPackage() throws Throwable {
        assumeTrue(isJava9OrLater());
        Field count = ByteArrayOutputStream.class.getDeclaredField("count");
        assertFalse(trySetAccessible(count));
        assertFalse(count.isAccessible());
    }

    @Test
    public void trySetAccessibleOnClassConstructorIsDeniedWithoutThrowingOnJava9OrLater() throws Throwable {
        // Class constructors can never be made accessible: Java 9+ reports false, Java 8 throws
        Constructor<?> classConstructor = Class.class.getDeclaredConstructors()[0];
        if (isJava9OrLater()) {
            assertFalse(trySetAccessible(classConstructor));
        } else {
            try {
                trySetAccessible(classConstructor);
                fail("SecurityException expected");
            } catch (SecurityException expected) {
                // setAccessible(true) refuses Class constructors on Java 8
            }
        }
    }

    // ---- getMethod(Class, Class...) ----

    @Test
    public void getMethodByParameterTypesAcceptsNullParameterTypes() {
        // an interface: coverage agents add synthetic no-arg methods to classes, which would make this ambiguous
        Method m = WhiteboxImpl.getMethod(NoArgAction.class, (Class<?>[]) null);
        assertEquals("act", m.getName());
        assertTrue(m.isAccessible());
    }

    @Test
    public void getMethodByParameterTypesSearchesInterfaces() {
        Method m = WhiteboxImpl.getMethod(StringConsumer.class, String.class);
        assertEquals("consume", m.getName());
    }

    @Test
    public void getMethodByParameterTypesSearchesSuperclass() {
        Method m = WhiteboxImpl.getMethod(SingleNoArgChild.class, long.class);
        assertEquals("withLong", m.getName());
    }

    @Test(expected = TooManyMethodsFoundException.class)
    public void getMethodByParameterTypesThrowsWhenAmbiguous() {
        WhiteboxImpl.getMethod(TwoNoArgs.class);
    }

    @Test(expected = MethodNotFoundException.class)
    public void getMethodByParameterTypesThrowsWhenNotFound() {
        WhiteboxImpl.getMethod(SingleNoArg.class, File.class, File.class);
    }

    // ---- getMethod(Class, String, Class...) ----

    @Test
    public void getMethodByNameAcceptsNullParameterTypesAndInterfaces() {
        assertEquals("noArg", WhiteboxImpl.getMethod(SingleNoArg.class, "noArg", (Class<?>[]) null).getName());
        assertEquals("consume", WhiteboxImpl.getMethod(StringConsumer.class, "consume", String.class).getName());
    }

    @Test(expected = MethodNotFoundException.class)
    public void getMethodByNameThrowsWhenNotFound() {
        WhiteboxImpl.getMethod(SingleNoArg.class, "doesNotExist");
    }

    // ---- getMethods / getAllMethods / getAllPublicMethods / doGetAllMethods ----

    @Test(expected = IllegalArgumentException.class)
    public void getMethodsRequiresMethodNames() {
        WhiteboxImpl.getMethods(SingleNoArg.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMethodsRejectsNullMethodNames() {
        WhiteboxImpl.getMethods(SingleNoArg.class, (String[]) null);
    }

    @Test
    public void getMethodsOnInterfaceUsesPublicMethods() {
        Method[] methods = WhiteboxImpl.getMethods(StringConsumer.class, "consume");
        assertEquals(1, methods.length);
        assertEquals("consume", methods[0].getName());
    }

    @Test
    public void getMethodsOnClassFindsPrivateMethodsAccessible() {
        Method[] methods = WhiteboxImpl.getMethods(TwoNoArgs.class, "first", "second");
        assertEquals(2, methods.length);
        for (Method m : methods) {
            assertTrue(m.isAccessible());
        }
    }

    @Test(expected = MethodNotFoundException.class)
    public void getMethodsThrowsWhenNothingMatches() {
        WhiteboxImpl.getMethods(TwoNoArgs.class, "nope", "nothing");
    }

    @Test
    public void getAllMethodsOfJdkClassInNonOpenedPackageDoesNotThrow() {
        // Private methods of ByteArrayOutputStream (java.io) cannot be opened on Java 9+; they are still listed
        Method[] methods = WhiteboxImpl.getAllMethods(ByteArrayOutputStream.class);
        boolean hasWriteTo = false;
        for (Method m : methods) {
            assertFalse("finalize must be skipped", "finalize".equals(m.getName())
                    && m.getDeclaringClass() == ByteArrayOutputStream.class);
            hasWriteTo |= "writeTo".equals(m.getName());
        }
        assertTrue(hasWriteTo);
        assertSame(methods, WhiteboxImpl.getAllMethods(ByteArrayOutputStream.class));
    }

    @Test
    public void doGetAllMethodsRejectsNull() throws Throwable {
        try {
            invokePrivateStatic("doGetAllMethods", new Class<?>[]{Class.class}, (Object) null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("specify a class"));
        }
    }

    @Test
    public void getAllPublicMethodsRejectsNull() throws Throwable {
        try {
            invokePrivateStatic("getAllPublicMethods", new Class<?>[]{Class.class}, (Object) null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("specify a class"));
        }
    }

    @Test
    public void getAllFieldsListsHierarchyAndRejectsNull() {
        Field[] fields = WhiteboxImpl.getAllFields(SingleNoArgChild.class);
        boolean hasChild = false;
        boolean hasParent = false;
        for (Field f : fields) {
            hasChild |= "childField".equals(f.getName());
            hasParent |= "parentField".equals(f.getName());
        }
        assertTrue(hasChild && hasParent);
        try {
            WhiteboxImpl.getAllFields(null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- getField(Class, String) ----

    @Test
    public void getFieldFindsFieldInSuperclassAndInterface() {
        Field parent = WhiteboxImpl.getField(SingleNoArgChild.class, "parentField");
        assertEquals(SingleNoArg.class, parent.getDeclaringClass());
        assertTrue(parent.isAccessible());
        Field constant = WhiteboxImpl.getField(SingleNoArgChild.class, "CONSTANT");
        assertEquals(WithConstant.class, constant.getDeclaringClass());
    }

    @Test
    public void getFieldOfJdkClassInNonOpenedPackageIsReturnedEvenIfInaccessible() {
        Field count = WhiteboxImpl.getField(ByteArrayOutputStream.class, "count");
        assertEquals(int.class, count.getType());
        assertEquals(!isJava9OrLater(), count.isAccessible());
    }

    @Test(expected = FieldNotFoundException.class)
    public void getFieldThrowsWhenNotFound() {
        WhiteboxImpl.getField(SingleNoArgChild.class, "doesNotExist");
    }

    // ---- findFieldOrThrowException via getInternalState / setInternalState(type, where) ----

    @Test
    public void getAndSetInternalStateByTypeAndWhere() {
        Holder holder = new Holder();
        WhiteboxImpl.setInternalState(holder, String.class, "set by type", Holder.class);
        assertEquals("set by type", WhiteboxImpl.getInternalState(holder, String.class, Holder.class));
    }

    @Test(expected = FieldNotFoundException.class)
    public void findFieldOrThrowExceptionThrowsWhenTypeNotDeclared() {
        WhiteboxImpl.getInternalState(new Holder(), File.class, Holder.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void findFieldOrThrowExceptionRejectsNullType() {
        WhiteboxImpl.getInternalState(new Holder(), (Class<?>) null, Holder.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setInternalStateByTypeAndWhereRejectsNull() {
        WhiteboxImpl.setInternalState(new Holder(), (Class<?>) null, "x", Holder.class);
    }

    // ---- findSingleFieldUsingStrategy ----

    @Test(expected = TooManyFieldsFoundException.class)
    public void setInternalStateByTypeThrowsWhenTwoFieldsMatch() {
        WhiteboxImpl.setInternalState(new TwoLongs(), long.class, 1L);
    }

    @Test(expected = FieldNotFoundException.class)
    public void setInternalStateByNameThrowsWhenNotFound() {
        WhiteboxImpl.setInternalState(new Holder(), "doesNotExist", 1);
    }

    // ---- setInternalState(Object, String, Object) and the Unsafe paths ----

    @Test
    public void setInternalStateSetsFinalInstanceFieldsOfEveryPrimitiveTypeViaUnsafe() {
        Holder h = new Holder();
        WhiteboxImpl.setInternalState(h, "finalInt", 42);
        WhiteboxImpl.setInternalState(h, "finalShort", (short) 43);
        WhiteboxImpl.setInternalState(h, "finalLong", 44L);
        WhiteboxImpl.setInternalState(h, "finalByte", (byte) 45);
        WhiteboxImpl.setInternalState(h, "finalBoolean", true);
        WhiteboxImpl.setInternalState(h, "finalFloat", 46.5f);
        WhiteboxImpl.setInternalState(h, "finalDouble", 47.5d);
        WhiteboxImpl.setInternalState(h, "finalChar", 'z');
        WhiteboxImpl.setInternalState(h, "finalObject", "changed");

        assertEquals(42, (int) WhiteboxImpl.<Integer>getInternalState(h, "finalInt"));
        assertEquals((short) 43, (short) WhiteboxImpl.<Short>getInternalState(h, "finalShort"));
        assertEquals(44L, (long) WhiteboxImpl.<Long>getInternalState(h, "finalLong"));
        assertEquals((byte) 45, (byte) WhiteboxImpl.<Byte>getInternalState(h, "finalByte"));
        assertTrue(WhiteboxImpl.<Boolean>getInternalState(h, "finalBoolean"));
        assertEquals(46.5f, WhiteboxImpl.<Float>getInternalState(h, "finalFloat"), 0f);
        assertEquals(47.5d, WhiteboxImpl.<Double>getInternalState(h, "finalDouble"), 0d);
        assertEquals('z', (char) WhiteboxImpl.<Character>getInternalState(h, "finalChar"));
        assertEquals("changed", WhiteboxImpl.getInternalState(h, "finalObject"));
    }

    @Test
    public void setInternalStateSetsPlainInstanceFieldViaReflection() {
        Holder h = new Holder();
        WhiteboxImpl.setInternalState(h, "plainInt", 7);
        assertEquals(7, h.plainInt());
    }

    @Test
    public void setInternalStateSetsInaccessibleJdkInstanceField() {
        // java.io is not opened: Java 9+ takes the Unsafe path, Java 8 plain reflection
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);
        out.write(2);
        WhiteboxImpl.setInternalState(out, "count", 1);
        assertEquals(1, out.size());
        assertArrayEquals(new byte[]{1}, out.toByteArray());
    }

    @Test
    public void setInternalStateSetsStaticFinalFieldViaUnsafe() {
        Object original = Holder.STATIC_FINAL;
        Object replacement = new Object();
        try {
            WhiteboxImpl.setInternalState(Holder.class, "STATIC_FINAL", replacement);
            assertSame(replacement, WhiteboxImpl.getInternalState(Holder.class, "STATIC_FINAL"));
        } finally {
            WhiteboxImpl.setInternalState(Holder.class, "STATIC_FINAL", original);
        }
        assertSame(original, WhiteboxImpl.getInternalState(Holder.class, "STATIC_FINAL"));
    }

    @Test
    public void setInternalStateSetsPlainStaticFieldViaReflection() {
        WhiteboxImpl.setInternalState(Holder.class, "staticPlain", 5);
        assertEquals(5, Holder.staticPlain);
    }

    @Test
    public void setInternalStateOnInaccessibleJdkStaticFieldUsesUnsafe() {
        // Writes the value that is already there, so the JDK stays unchanged
        String separator = File.separator;
        WhiteboxImpl.setInternalState(File.class, "separator", separator);
        assertEquals(separator, File.separator);
    }

    @Test
    public void unsafeInstancePathWrapsFailures() {
        try {
            WhiteboxImpl.setInternalState(new Holder(), "finalInt", "not an int");
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void unsafeStaticPathWrapsFailures() {
        try {
            WhiteboxImpl.setInternalState(Holder.class, "STATIC_FINAL_INT", "not an int");
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void reflectiveStaticPathWrapsIllegalArgument() {
        try {
            WhiteboxImpl.setInternalState(Holder.class, "staticPlain", "not an int");
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void reflectiveInstancePathPropagatesIllegalArgument() {
        WhiteboxImpl.setInternalState(new Holder(), "plainInt", "not an int");
    }

    // ---- setInternalState(Object, String, Object, Class) ----

    @Test
    public void setInternalStateWithWhereSetsOwnField() {
        Holder h = new Holder();
        WhiteboxImpl.setInternalState(h, "plainInt", 9, Holder.class);
        assertEquals(9, h.plainInt());
    }

    @Test
    public void setInternalStateWithWhereSetsInaccessibleJdkField() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        out.write(4);
        WhiteboxImpl.setInternalState(out, "count", 1, ByteArrayOutputStream.class);
        assertArrayEquals(new byte[]{3}, out.toByteArray());
    }

    @Test
    public void setInternalStateWithWhereWrapsFailures() {
        try {
            WhiteboxImpl.setInternalState(new Holder(), "plainInt", "not an int", Holder.class);
            fail("RuntimeException expected");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to set field"));
        }
    }

    @Test
    public void setInternalStateWithWhereValidatesArguments() {
        String[] badNames = {null, "", " leading"};
        for (String name : badNames) {
            try {
                WhiteboxImpl.setInternalState(new Holder(), name, 1, Holder.class);
                fail("IllegalArgumentException expected for " + name);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        try {
            WhiteboxImpl.setInternalState(null, "plainInt", 1, Holder.class);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            WhiteboxImpl.setInternalState(new Holder(), "plainInt", 1, null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("where"));
        }
    }

    @Test(expected = FieldNotFoundException.class)
    public void setInternalStateWithWhereThrowsWhenFieldNotDeclaredThere() {
        WhiteboxImpl.setInternalState(new SingleNoArgChild(), "parentField", 1, SingleNoArgChild.class);
    }

    // ---- helper types ----

    @SuppressWarnings("unused")
    static class Holder {
        static final Object STATIC_FINAL = new Object();
        static final int STATIC_FINAL_INT = Integer.parseInt("1");
        static int staticPlain;

        private final int finalInt;
        private final short finalShort;
        private final long finalLong;
        private final byte finalByte;
        private final boolean finalBoolean;
        private final float finalFloat;
        private final double finalDouble;
        private final char finalChar;
        private final Object finalObject;
        private final String text;
        private int plainInt;

        Holder() {
            finalInt = 1;
            finalShort = 2;
            finalLong = 3;
            finalByte = 4;
            finalBoolean = false;
            finalFloat = 5f;
            finalDouble = 6d;
            finalChar = 'a';
            finalObject = "original";
            text = "text";
        }

        int plainInt() {
            return plainInt;
        }
    }

    interface WithConstant {
        String CONSTANT = "constant";
    }

    @SuppressWarnings("unused")
    static class SingleNoArg {
        private int parentField;

        private void noArg() {
        }

        private void withLong(long l) {
        }
    }

    @SuppressWarnings("unused")
    static class SingleNoArgChild extends SingleNoArg implements WithConstant {
        private int childField;

        private void withInt(int i) {
        }
    }

    @SuppressWarnings("unused")
    static class TwoNoArgs {
        private void first() {
        }

        private void second() {
        }
    }

    @SuppressWarnings("unused")
    static class TwoLongs {
        private long a;
        private long b;
    }

    interface NoArgAction {
        void act();
    }

    interface StringConsumer {
        void consume(String s);
    }
}
