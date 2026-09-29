package powermock.modules.junit5.gaps2.c5templates;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.Ticket;
import powermock.modules.junit5.gaps2.support.Tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C5: @ParameterizedTest with @ValueSource/@CsvSource. Jupiter creates the arguments outside the test (implicit
 * conversion to a user class and to a user enum, Class literals); inside the test they must be the prepared
 * classes' objects: static mocking inside their methods, final-method stubbing, enum constants and Class
 * literals identical to the ones the test code sees.
 * 8 invocations; the two primitive-argument invocations are controls.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Ids.class, Ticket.class, Tier.class})
class C5ValueAndCsvSourceTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void controlPrimitiveArgumentsWithStaticMocking(int n) {
        assertEquals("real-id", Ids.next());
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("n" + n);
        assertEquals("n" + n, Ids.next());
    }

    @ParameterizedTest
    @ValueSource(classes = Ids.class)
    void classLiteralArgumentIsThePreparedClass(Class<?> type) {
        assertSame(Ids.class, type, "Class literal from @ValueSource must be the class the test code uses");
        mockStatic(type);
        when(Ids.next()).thenReturn("via-class-argument");
        assertEquals("via-class-argument", Ids.next());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A-1", "B-2"})
    void implicitlyConvertedUserObjectUsesMockedStatic(Ticket ticket) {
        assertEquals(ticket.code() + "@real-id", ticket.describe());
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("stub");
        assertEquals(ticket.code() + "@stub", ticket.describe());
    }

    @ParameterizedTest
    @CsvSource({"GOLD, gold", "BRONZE, bronze"})
    void csvEnumColumnIsTheTestsEnumConstant(Tier tier, String lower) {
        assertSame(Tier.valueOf(lower.toUpperCase()), tier);
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("s");
        assertEquals(lower + "-s", tier.tagged());
    }

    @ParameterizedTest
    @CsvSource({"T-9, 3"})
    void csvConvertedUserObjectFinalMethodStubbedOnSpy(Ticket ticket, int length) {
        assertEquals(length, ticket.code().length());
        Ticket spied = spy(ticket);
        doReturn("stubbed-code").when(spied).code();
        assertEquals("stubbed-code", spied.code());
        assertEquals("T-9", ticket.code());
    }
}
