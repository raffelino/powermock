package powermock.modules.junit5.gaps2.c5templates;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.Order;
import powermock.modules.junit5.gaps2.support.TaxRates;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C5: @MethodSource supplying objects of a prepared user class (created by the factory method, which Jupiter
 * calls itself): inside the test they must use the mocked static and allow final-method stubbing.
 * @RepeatedTest with RepetitionInfo: fresh PowerMock state in every repetition.
 * 6 invocations; the two repetitions are controls.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Ids.class, Order.class, TaxRates.class})
class C5MethodSourceAndRepeatedTest {

    static Stream<Arguments> orders() {
        return Stream.of(arguments(new Order("o-1", 100), 120), arguments(new Order("o-2", 50), 60));
    }

    @ParameterizedTest
    @MethodSource("orders")
    void sourcedUserObjectsUseMockedStatic(Order order, int grossAtTwentyPercent) {
        assertEquals(grossAtTwentyPercent, order.gross());
        mockStatic(TaxRates.class);
        when(TaxRates.percent()).thenReturn(0);
        assertEquals(order.net(), order.gross());
    }

    @ParameterizedTest
    @MethodSource("orders")
    void sourcedUserObjectsFinalMethodStubbedOnSpy(Order order, int grossAtTwentyPercent) {
        Order spied = spy(order);
        when(spied.net()).thenReturn(10);
        assertEquals(10, spied.net());
        assertEquals(12, spied.gross());
        assertEquals(order.id(), spied.id());
        assertEquals(grossAtTwentyPercent, order.gross());
    }

    @RepeatedTest(2)
    void controlFreshPowerMockStatePerRepetition(RepetitionInfo info) {
        assertEquals(2, info.getTotalRepetitions());
        assertEquals("real-id", Ids.next(), "static mock of an earlier repetition leaked");
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("rep-" + info.getCurrentRepetition());
        Order order = mock(Order.class);
        when(order.net()).thenReturn(info.getCurrentRepetition());
        assertEquals("rep-" + info.getCurrentRepetition(), Ids.next());
        assertEquals(info.getCurrentRepetition(), order.net());
        assertTrue(info.getCurrentRepetition() >= 1);
    }
}
