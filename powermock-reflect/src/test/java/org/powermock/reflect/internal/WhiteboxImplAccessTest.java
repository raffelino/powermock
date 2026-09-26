package org.powermock.reflect.internal;

import org.junit.Test;
import org.powermock.reflect.exceptions.FieldNotFoundException;
import org.powermock.reflect.internal.AccessTargets.Iface;
import org.powermock.reflect.internal.AccessTargets.Prims;
import org.powermock.reflect.internal.AccessTargets.TwoFields;
import org.powermock.reflect.internal.AccessTargets.TwoMethods;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for the accessibility handling (trySetAccessible) of WhiteboxImpl and the Unsafe based field setters.
 */
public class WhiteboxImplAccessTest {

    @Test
    public void getFieldReturnsTheNamedFieldReadyToRead() throws Exception {
        Field field = WhiteboxImpl.getField(TwoFields.class, "second");
        assertEquals("second", field.getName());
        assertEquals("s", field.get(new TwoFields()));
    }

    @Test(expected = FieldNotFoundException.class)
    public void getFieldThrowsForUnknownField() {
        WhiteboxImpl.getField(TwoFields.class, "missing");
    }

    @Test
    public void setInternalStateByTypeAndWhereSetsOnlyTheFieldOfThatType() {
        TwoFields target = new TwoFields();
        WhiteboxImpl.setInternalState(target, String.class, "new", TwoFields.class);
        assertEquals("new", WhiteboxImpl.getInternalState(target, "second"));
        assertEquals(1, (int) WhiteboxImpl.<Integer>getInternalState(target, "first"));
    }

    @Test
    public void getMethodByParameterTypesFindsPrivateMethodAndMakesItAccessible() throws Exception {
        Method method = WhiteboxImpl.getMethod(TwoMethods.class, StringBuilder.class);
        assertEquals("priv", method.getName());
        assertEquals("priv", method.invoke(new TwoMethods(), new StringBuilder()));
    }

    @Test
    public void getMethodByParameterTypesOnInterface() {
        assertEquals("onlyOne", WhiteboxImpl.getMethod(Iface.class, Character.class).getName());
    }

    @Test
    public void getMethodByNameMakesPrivateMethodAccessible() throws Exception {
        Method method = WhiteboxImpl.getMethod(TwoMethods.class, "other", Integer.class);
        assertEquals("other", method.invoke(new TwoMethods(), 1));
    }

    @Test
    public void getMethodsOnInterfaceUsesPublicMethods() {
        Method[] methods = WhiteboxImpl.getMethods(Iface.class, "onlyOne");
        assertEquals(1, methods.length);
        assertEquals("onlyOne", methods[0].getName());
    }

    @Test
    public void getMethodsMakesPrivateMethodsAccessible() throws Exception {
        Method[] methods = WhiteboxImpl.getMethods(TwoMethods.class, "priv");
        assertEquals(1, methods.length);
        assertEquals("priv", methods[0].invoke(new TwoMethods(), new StringBuilder()));
    }

    @Test
    public void setInternalStateSetsFinalFieldsOfEveryPrimitiveType() {
        Prims p = new Prims(1);
        WhiteboxImpl.setInternalState(p, "i", 7);
        WhiteboxImpl.setInternalState(p, "s", (short) 7);
        WhiteboxImpl.setInternalState(p, "l", 7L);
        WhiteboxImpl.setInternalState(p, "b", (byte) 7);
        WhiteboxImpl.setInternalState(p, "z", true);
        WhiteboxImpl.setInternalState(p, "f", 7f);
        WhiteboxImpl.setInternalState(p, "d", 7d);
        WhiteboxImpl.setInternalState(p, "c", 'x');
        WhiteboxImpl.setInternalState(p, "o", "seven");

        assertEquals(7, (int) WhiteboxImpl.<Integer>getInternalState(p, "i"));
        assertEquals((short) 7, (short) WhiteboxImpl.<Short>getInternalState(p, "s"));
        assertEquals(7L, (long) WhiteboxImpl.<Long>getInternalState(p, "l"));
        assertEquals((byte) 7, (byte) WhiteboxImpl.<Byte>getInternalState(p, "b"));
        assertEquals(true, WhiteboxImpl.<Boolean>getInternalState(p, "z"));
        assertEquals(7f, WhiteboxImpl.<Float>getInternalState(p, "f"), 0f);
        assertEquals(7d, WhiteboxImpl.<Double>getInternalState(p, "d"), 0d);
        assertEquals('x', (char) WhiteboxImpl.<Character>getInternalState(p, "c"));
        assertEquals("seven", WhiteboxImpl.getInternalState(p, "o"));
    }

    @Test
    public void setInternalStateSetsStaticFinalFields() {
        int oldInt = WhiteboxImpl.<Integer>getInternalState(Prims.class, "STATIC_INT");
        String oldString = WhiteboxImpl.getInternalState(Prims.class, "STATIC_STRING");
        try {
            WhiteboxImpl.setInternalState(Prims.class, "STATIC_INT", 42);
            WhiteboxImpl.setInternalState(Prims.class, "STATIC_STRING", "b");
            assertEquals(42, (int) WhiteboxImpl.<Integer>getInternalState(Prims.class, "STATIC_INT"));
            assertEquals("b", WhiteboxImpl.getInternalState(Prims.class, "STATIC_STRING"));
        } finally {
            WhiteboxImpl.setInternalState(Prims.class, "STATIC_INT", oldInt);
            WhiteboxImpl.setInternalState(Prims.class, "STATIC_STRING", oldString);
        }
    }

    /**
     * java.io is not opened to PowerMock, so on Java 9+ the field cannot be made accessible and must be set via
     * Unsafe; on Java 8 the plain reflective path is used. Both must work.
     */
    @Test
    public void setInternalStateWithWhereSetsFieldOfUnopenedJdkPackage() throws Exception {
        StringReader reader = new StringReader("a");
        WhiteboxImpl.setInternalState(reader, "str", "xy", StringReader.class);
        assertEquals('x', reader.read());
    }

    @Test
    public void setInternalStateByNameAndTypeRejectsNullObject() {
        try {
            WhiteboxImpl.setInternalState(null, (Object) "value", TwoFields.class);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("The object containing the field cannot be null", e.getMessage());
        }
    }
}
