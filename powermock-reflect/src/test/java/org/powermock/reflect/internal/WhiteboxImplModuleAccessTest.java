package org.powermock.reflect.internal;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class WhiteboxImplModuleAccessTest {

    @Test
    public void getAllMethodsDoesNotFailForClosedJdkPackage() {
        assumeTrue("module system requires Java 9+", !System.getProperty("java.specification.version").startsWith("1."));

        Method[] methods = WhiteboxImpl.getAllMethods(ConcurrentHashMap.class);

        assertTrue(methods.length > 0);
    }

    @Test
    public void privateMethodOfUserClassIsAccessibleAndInvocable() throws Exception {
        Method found = null;
        for (Method method : WhiteboxImpl.getAllMethods(UserClass.class)) {
            if (method.getName().equals("secret")) {
                found = method;
            }
        }

        assertTrue(found != null);
        assertTrue(found.isAccessible());
        assertEquals("secret", Whitebox.invokeMethod(new UserClass(), "secret"));
    }

    private static class UserClass {
        private String secret() {
            return "secret";
        }
    }
}
