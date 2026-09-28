package powermock.modules.junit5.gaps.c1annotations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;
import powermock.modules.junit5.gaps.support.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/** C1: @Mock fields (plain class and FINAL prepared class) under PowerMockExtension, like PowerMockRunner. */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Greeter.class, IdGenerator.class})
class C1MockAnnotationTest {

    @Mock
    private UserRepository repository;

    @Mock
    private Greeter greeter;

    @Test
    void mockOfFinalPreparedClassCanBeStubbedAndVerified() {
        assertNotNull(greeter, "@Mock Greeter (final, prepared) must be initialised");
        assertTrue(mockingDetails(greeter).isMock());
        when(greeter.greet("world")).thenReturn("stubbed");

        assertEquals("stubbed", greeter.greet("world"));
        assertNull(greeter.greet("other"));
        verify(greeter).greet("world");
    }

    @Test
    void mocksAreFreshForEachTestA() {
        assertNotNull(repository);
        assertTrue(mockingDetails(repository).getInvocations().isEmpty(), "@Mock must be fresh for each test");
        assertTrue(mockingDetails(greeter).getInvocations().isEmpty(), "@Mock must be fresh for each test");
        when(repository.find(1)).thenReturn("stub-a");
        assertEquals("stub-a", repository.find(1));
        greeter.greet("a");
    }

    @Test
    void mocksAreFreshForEachTestB() {
        assertNotNull(repository);
        assertTrue(mockingDetails(repository).getInvocations().isEmpty(), "@Mock must be fresh for each test");
        assertTrue(mockingDetails(greeter).getInvocations().isEmpty(), "@Mock must be fresh for each test");
        assertNull(repository.find(1), "stubbing of another test must not leak");
        greeter.greet("b");
    }

    @Test
    void annotatedMockCombinedWithStaticMocking() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("static-stub");
        when(repository.find(7)).thenReturn("user-7");

        assertEquals("user-7#static-stub", repository.find(7) + "#" + IdGenerator.next());
    }

    @Test
    void controlProgrammaticMockOfFinalClassWorks() {
        Greeter programmatic = mock(Greeter.class);
        when(programmatic.greet("x")).thenReturn("y");
        assertEquals("y", programmatic.greet("x"));
    }
}
