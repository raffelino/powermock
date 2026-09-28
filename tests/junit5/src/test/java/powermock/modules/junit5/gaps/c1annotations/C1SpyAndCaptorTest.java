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

/** C1: @Spy (initialised and uninitialised field) and @Captor under PowerMockExtension. */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest(Calculator.class)
class C1SpyAndCaptorTest {

    @Spy
    private Calculator calculator = new Calculator();

    @Spy
    private Calculator defaultConstructedSpy;

    @Mock
    private UserRepository repository;

    @Captor
    private ArgumentCaptor<Integer> idCaptor;

    @Test
    void spyCallsRealMethodsAndCanBePartiallyStubbed() {
        assertTrue(mockingDetails(calculator).isSpy(), "@Spy field must be turned into a spy");
        assertEquals(4, calculator.twice(2));
        doReturn(100).when(calculator).add(3, 3);
        assertEquals(100, calculator.twice(3));
        verify(calculator).add(2, 2);
    }

    @Test
    void spyOfPreparedClassCanStubFinalMethod() {
        assertNotNull(defaultConstructedSpy, "@Spy without initialiser must be created with the no-arg constructor");
        assertTrue(mockingDetails(defaultConstructedSpy).isSpy());
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
