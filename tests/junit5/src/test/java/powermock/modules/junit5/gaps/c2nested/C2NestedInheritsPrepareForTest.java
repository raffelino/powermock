package powermock.modules.junit5.gaps.c2nested;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/** C2: a @Nested class inherits the enclosing class's @PrepareForTest. */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({IdGenerator.class, Greeter.class})
class C2NestedInheritsPrepareForTest {

    @Test
    void controlStaticMockingInOuterClass() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("outer");
        assertEquals("outer", IdGenerator.next());
    }

    @Nested
    class Inner {

        @Test
        void staticMockingWorksInNestedTest() {
            mockStatic(IdGenerator.class);
            when(IdGenerator.next()).thenReturn("nested");
            assertEquals("nested", IdGenerator.next());
            verifyStatic(IdGenerator.class);
            IdGenerator.next();
        }

        @Test
        void finalClassMockingWorksInNestedTest() {
            Greeter greeter = mock(Greeter.class);
            when(greeter.greet("n")).thenReturn("nested-final");
            assertEquals("nested-final", greeter.greet("n"));
        }
    }
}
