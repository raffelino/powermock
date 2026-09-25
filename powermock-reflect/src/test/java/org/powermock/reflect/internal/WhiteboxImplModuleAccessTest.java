package org.powermock.reflect.internal;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WhiteboxImplModuleAccessTest {

    @SuppressWarnings("unused")
    private static class UserClass {
        private String secret() {
            return "secret";
        }
    }

    @Test
    public void getAllMethodsDoesNotFailForJdkClassInPackageNotOpened() {
        // java.util.logging is not opened to the unnamed module on JDK 9+.
        assertTrue(WhiteboxImpl.getAllMethods(Logger.class).length > 0);
        assertTrue(WhiteboxImpl.getMethods(Logger.class, "getLogger").length > 0);
    }

    @Test
    public void privateMethodsOfUserClassesAreStillAccessible() throws Exception {
        Method[] methods = WhiteboxImpl.getMethods(UserClass.class, "secret");
        assertEquals(1, methods.length);
        assertEquals("secret", methods[0].invoke(new UserClass()));
    }
}
