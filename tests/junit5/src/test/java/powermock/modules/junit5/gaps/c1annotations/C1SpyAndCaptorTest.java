package powermock.modules.junit5.gaps.c1annotations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Calculator;
import powermock.modules.junit5.gaps.support.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

/**
 * C1: @Spy and @Captor under PowerMockExtension. Both @Spy fields carry an initialiser, as
 * PowerMockRunner's PowerMockitoSpyAnnotationEngine requires; spies are checked with isMock(),
 * because PowerMockito spies use POWER_MOCK_CALL_REAL_METHOD, which Mockito's isSpy() does not
 * recognise. Real behaviour is proven by the real return values.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest(Calculator.class)
class C1SpyAndCaptorTest {

    @Spy
    private Calculator calculator = new Calculator();

    @Spy
    private Calculator defaultConstructedSpy = new Calculator();

    @Mock
    private UserRepository repository;

    @Captor
    private ArgumentCaptor<Integer> idCaptor;

    @Test
    void spyCallsRealMethodsAndCanBePartiallyStubbed() {
        assertTrue(mockingDetails(calculator).isMock(), "@Spy field must be replaced by a PowerMock spy");
        assertEquals(4, calculator.twice(2));
        doReturn(100).when(calculator).add(3, 3);
        assertEquals(100, calculator.twice(3));
        verify(calculator).add(2, 2);
    }

    @Test
    void spyOfPreparedClassCanStubFinalMethod() {
        assertNotNull(defaultConstructedSpy, "@Spy field with initialiser must not be null");
        assertTrue(mockingDetails(defaultConstructedSpy).isMock(), "@Spy field must be replaced by a PowerMock spy");
        assertEquals(41, defaultConstructedSpy.answer());
        doReturn(42).when(defaultConstructedSpy).answer();
        assertEquals(42, defaultConstructedSpy.answer());
    }

    @Test
    void captorCapturesArgumentsPassedToMock() {
        assertNotNull(idCaptor, "@Captor must be initialised");
        repository.find(11);
        repository.find(12);
        verify(repository, org.mockito.Mockito.times(2)).find(idCaptor.capture());
        assertEquals(java.util.Arrays.asList(11, 12), idCaptor.getAllValues());
    }
}
