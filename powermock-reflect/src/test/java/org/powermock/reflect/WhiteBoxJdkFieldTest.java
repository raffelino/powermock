package org.powermock.reflect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Setting a private field of a JDK class must work even when its package is not opened (Java 9+).
 */
public class WhiteBoxJdkFieldTest {

    @Test
    public void canSetPrivateFieldOfJdkClass() {
        AssertionError error = new AssertionError("old");

        Whitebox.setInternalState(error, "detailMessage", "new");

        assertEquals("new", error.getMessage());
    }
}
