package powermock.modules.junit5.gaps.c2nested;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.AuditLog;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;
import powermock.modules.junit5.gaps.support.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/** C2: a @Nested class adds its own @PrepareForTest on top of the enclosing one (both apply inside it). */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest(IdGenerator.class)
class C2NestedOwnPrepareForTest {

    @Test
    void controlOuterPreparedClassMockable() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("outer");
        assertEquals("outer", IdGenerator.next());
    }

    @Nested
    @PrepareForTest({Greeter.class, UserService.class})
    class WithMorePreparedClasses {

        @Test
        void nestedPreparedFinalClassAndOuterPreparedStaticBothWork() {
            Greeter greeter = mock(Greeter.class);
            when(greeter.greet("x")).thenReturn("nested-prepared");
            mockStatic(IdGenerator.class);
            when(IdGenerator.next()).thenReturn("outer-prepared");

            assertEquals("nested-prepared/outer-prepared", greeter.greet("x") + "/" + IdGenerator.next());
        }

        @Test
        void whenNewWorksForClassPreparedOnlyByNestedClass() throws Exception {
            AuditLog auditLog = mock(AuditLog.class);
            when(auditLog.record("e")).thenReturn("intercepted");
            whenNew(AuditLog.class).withNoArguments().thenReturn(auditLog);

            assertEquals("intercepted", new UserService().audit("e"));
        }
    }
}
