package powermock.modules.junit5.gaps.c1annotations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Calculator;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C1: @Mock fields are already initialised when @BeforeEach runs (as with PowerMockRunner),
 * stubbing done in @BeforeEach is seen by the test, and @AfterEach can verify on them.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Greeter.class, Calculator.class, IdGenerator.class})
class C1AnnotatedMocksInLifecycleTest {

    @Mock
    private Greeter greeter;

    @Mock
    private Calculator calculator;

    @BeforeEach
    void stubInBeforeEach() {
        assertNotNull(greeter, "@Mock must be initialised before @BeforeEach");
        when(greeter.greet("x")).thenReturn("from-before-each");
    }

    @AfterEach
    void verifyInAfterEach() {
        verify(greeter).greet("x");
    }

    @Test
    void stubbingFromBeforeEachIsSeenByTest() {
        assertEquals("from-before-each", greeter.greet("x"));
    }

    @Test
    void finalMethodOfPreparedClassMockCanBeStubbed() {
        when(calculator.answer()).thenReturn(42);
        assertEquals(42, calculator.answer());
        assertEquals("from-before-each", greeter.greet("x"));
    }

    @Test
    void annotatedMocksAndStaticMockingTogether() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("s");
        assertEquals("from-before-each/s", greeter.greet("x") + "/" + IdGenerator.next());
    }
}
