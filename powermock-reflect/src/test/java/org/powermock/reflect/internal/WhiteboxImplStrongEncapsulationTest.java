package org.powermock.reflect.internal;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class WhiteboxImplStrongEncapsulationTest {

    @Test
    public void getAllMethodsDoesNotFailForClassesInPackagesThatAreNotOpened() {
        // java.io is not opened to the unnamed module on JDK 9+; setAccessible on its private methods throws there.
        assertTrue(WhiteboxImpl.getAllMethods(java.io.File.class).length > 0);
    }
}
