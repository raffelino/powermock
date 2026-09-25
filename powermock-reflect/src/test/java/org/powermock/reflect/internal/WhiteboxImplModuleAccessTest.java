package org.powermock.reflect.internal;

import org.junit.Assume;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WhiteboxImplModuleAccessTest {

    @Test
    public void getAllMethodsDoesNotFailForClassInNotOpenedJdkPackage() {
        Assume.assumeTrue(isJava9OrLater());
        Method[] methods = WhiteboxImpl.getAllMethods(ConcurrentHashMap.class);
        assertTrue(methods.length > 0);
    }

    @Test
    public void privateMethodsOfUserClassesAreStillAccessibleAndInvocable() throws Exception {
        Method found = null;
        for (Method method : WhiteboxImpl.getAllMethods(UserClass.class)) {
            if ("secret".equals(method.getName())) {
                found = method;
            }
        }
        assertNotNull(found);
        assertTrue(found.isAccessible());
        assertEquals("secret", Whitebox.invokeMethod(new UserClass(), "secret"));
    }

    private static boolean isJava9OrLater() {
        try {
            Class.forName("java.lang.Module");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static class UserClass {
        private String secret() {
            return "secret";
        }
    }
}
