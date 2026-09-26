package org.powermock.reflect.internal;

import org.junit.Test;
import org.powermock.reflect.exceptions.FieldNotFoundException;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the module-access handling ({@code trySetAccessible}) of {@link WhiteboxImpl} and the
 * reflection / Unsafe paths that depend on it. The helper classes are top-level (not nested) so that
 * nestmate access on Java 11+ cannot make private members reachable without setAccessible.
 */
public class WhiteboxImplAccessTest {

    @Test
    public void getMethodByParameterTypesReturnsAccessiblePrivateMethod() throws Exception {
        Method method = WhiteboxImpl.getMethod(AccessHelperMethods.class, StringBuilder.class);
        assertEquals("secret", method.getName());
        assertEquals("secret:x", method.invoke(new AccessHelperMethods(), new StringBuilder("x")));
    }

    @Test
    public void getMethodByNameReturnsAccessiblePrivateMethod() throws Exception {
        Method method = WhiteboxImpl.getMethod(AccessHelperMethods.class, "secret", StringBuilder.class);
        assertEquals("secret:y", method.invoke(new AccessHelperMethods(), new StringBuilder("y")));
    }

    @Test
    public void getMethodsOnInterfaceUsesPublicMethods() {
        Method[] methods = WhiteboxImpl.getMethods(Runnable.class, "run");
        assertEquals(1, methods.length);
        assertEquals("run", methods[0].getName());
    }

    @Test
    public void getMethodByParameterTypesOnInterface() {
        Method method = WhiteboxImpl.getMethod(Comparable.class, Object.class);
        assertEquals("compareTo", method.getName());
        assertEquals(Comparable.class, method.getDeclaringClass());
    }

    @Test
    public void getMethodByNameOnInterface() {
        Method method = WhiteboxImpl.getMethod(Callable.class, "call");
        assertEquals(Callable.class, method.getDeclaringClass());
    }

    @Test
    public void getFieldReturnsFieldWithRequestedName() throws Exception {
        Field field = WhiteboxImpl.getField(AccessHelperFields.class, "second");
        assertEquals("second", field.getName());
        assertEquals("2", field.get(new AccessHelperFields()));
    }

    @Test
    public void getFieldThrowsWhenFieldIsMissing() {
        try {
            WhiteboxImpl.getField(AccessHelperFields.class, "doesNotExist");
            fail("expected FieldNotFoundException");
        } catch (FieldNotFoundException e) {
            assertTrue(e.getMessage().contains("doesNotExist"));
        }
    }

    @Test
    public void setInternalStateByFieldTypeAndWhereSetsMatchingField() {
        AccessHelperFields object = new AccessHelperFields();
        WhiteboxImpl.setInternalState(object, Integer.class, 42, AccessHelperFields.class);
        assertEquals(Integer.valueOf(42), object.number);
        assertEquals("1", object.first);
        assertEquals("2", object.second);
    }

    @Test
    public void setInternalStateByValueAndWhereRejectsNullObject() {
        try {
            WhiteboxImpl.setInternalState(null, (Object) "value", AccessHelperFields.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("The object containing the field cannot be null", e.getMessage());
        }
    }

    @Test
    public void setInternalStateWithWhereSetsFieldOfJdkClass() {
        // java.io is not opened to PowerMock on Java 9+, so File.path cannot be made accessible
        File file = new File("before");
        WhiteboxImpl.setInternalState(file, "path", "after", File.class);
        assertEquals("after", file.getPath());
    }

    @Test
    public void setInternalStateSetsFieldOfJdkClass() {
        File file = new File("before");
        WhiteboxImpl.setInternalState(file, "path", "after");
        assertEquals("after", file.getPath());
    }

    @Test
    public void setInternalStateWidensValueForAccessibleInstanceField() {
        // field.set widens Integer to long; the Unsafe path would fail with a ClassCastException
        AccessHelperPrimitives object = new AccessHelperPrimitives();
        WhiteboxImpl.setInternalState(object, "mutableLong", 5);
        assertEquals(5L, object.mutableLong);
    }

    @Test
    public void setInternalStateWidensValueForAccessibleStaticField() {
        long old = AccessHelperPrimitives.staticLong;
        try {
            WhiteboxImpl.setInternalState(AccessHelperPrimitives.class, "staticLong", 7);
            assertEquals(7L, AccessHelperPrimitives.staticLong);
        } finally {
            AccessHelperPrimitives.staticLong = old;
        }
    }

    @Test
    public void setInternalStateSetsFinalPrimitiveFieldsUsingUnsafe() {
        AccessHelperPrimitives object = new AccessHelperPrimitives();
        WhiteboxImpl.setInternalState(object, "i", 42);
        WhiteboxImpl.setInternalState(object, "s", (short) 43);
        WhiteboxImpl.setInternalState(object, "l", 44L);
        WhiteboxImpl.setInternalState(object, "b", (byte) 45);
        WhiteboxImpl.setInternalState(object, "z", true);
        WhiteboxImpl.setInternalState(object, "f", 46.5f);
        WhiteboxImpl.setInternalState(object, "d", 47.5d);
        WhiteboxImpl.setInternalState(object, "c", 'q');
        WhiteboxImpl.setInternalState(object, "o", "new");

        assertEquals(42, (int) (Integer) WhiteboxImpl.getInternalState(object, "i"));
        assertEquals((short) 43, (short) (Short) WhiteboxImpl.getInternalState(object, "s"));
        assertEquals(44L, (long) (Long) WhiteboxImpl.getInternalState(object, "l"));
        assertEquals((byte) 45, (byte) (Byte) WhiteboxImpl.getInternalState(object, "b"));
        assertEquals(Boolean.TRUE, WhiteboxImpl.getInternalState(object, "z"));
        assertEquals(46.5f, (Float) WhiteboxImpl.getInternalState(object, "f"), 0f);
        assertEquals(47.5d, (Double) WhiteboxImpl.getInternalState(object, "d"), 0d);
        assertEquals(Character.valueOf('q'), WhiteboxImpl.getInternalState(object, "c"));
        assertEquals("new", WhiteboxImpl.getInternalState(object, "o"));
    }
}

class AccessHelperMethods {
    private String secret(StringBuilder value) {
        return "secret:" + value;
    }
}

class AccessHelperFields {
    String first = "1";
    String second = "2";
    Integer number = 0;
}

class AccessHelperPrimitives {
    static long staticLong = 1L;

    long mutableLong = 1L;

    private final int i;
    private final short s;
    private final long l;
    private final byte b;
    private final boolean z;
    private final float f;
    private final double d;
    private final char c;
    private final Object o;

    AccessHelperPrimitives() {
        i = Integer.parseInt("1");
        s = (short) i;
        l = i;
        b = (byte) i;
        z = i == 0;
        f = i;
        d = i;
        c = 'a';
        o = "old";
    }
}
