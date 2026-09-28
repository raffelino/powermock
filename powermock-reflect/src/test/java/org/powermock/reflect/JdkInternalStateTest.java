package org.powermock.reflect;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * Internal state of JDK classes must be readable and writable on every Java version, also when the JDK
 * package is not opened to PowerMock (Java 9+ module system).
 */
public class JdkInternalStateTest {

    @Test
    public void setsAndGetsPrivateFieldOfJdkClass() {
        Throwable throwable = new Throwable("old");

        Whitebox.setInternalState(throwable, "detailMessage", "new");

        assertEquals("new", throwable.getMessage());
        assertEquals("new", Whitebox.getInternalState(throwable, "detailMessage"));
        assertEquals("new", Whitebox.getInternalState(throwable, "detailMessage", Throwable.class));
    }

    @Test
    public void getsPrimitivePrivateFieldOfJdkClass() {
        ArrayList<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");

        assertEquals(2, (int) Whitebox.<Integer>getInternalState(list, "size"));
    }

    @Test
    public void setsPrimitivePrivateFieldOfJdkClass() {
        ArrayList<String> list = new ArrayList<String>();
        list.add("a");
        list.add("b");

        Whitebox.setInternalState(list, "size", 1);

        assertEquals(1, list.size());
    }
}
