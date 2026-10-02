package powermock.modules.junit5.gaps2.c7exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.CheckedUserFailure;
import powermock.modules.junit5.gaps2.support.UserFailure;
import powermock.modules.junit5.gaps2.support.UserFailureHandler;
import powermock.modules.junit5.gaps2.support.Validator;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C7: a user exception thrown from a prepared class reaches a TestExecutionExceptionHandler as that user type:
 * {@link UserFailureHandler} swallows it only if {@code instanceof UserFailure} (so the test passes), and its
 * AfterTestExecutionCallback requires that it did. Thrown by the real prepared class, by a stubbed static
 * method, and from a test body running in a separate thread (@Timeout SEPARATE_THREAD).
 * 4 tests; assertThrows inside the test is the control.
 */
@ExtendWith({PowerMockExtension.class, UserFailureHandler.class})
@PrepareForTest(Validator.class)
class C7ExceptionHandlerTest {

    @Test
    void handlerSwallowsUserFailureFromPreparedClass() {
        Validator.check("");
    }

    @Test
    void handlerSwallowsUserFailureFromStubbedStatic() {
        mockStatic(Validator.class);
        doThrow(new UserFailure(Validator.EXPECTED_PREFIX + "stubbed")).when(Validator.class);
        Validator.check("valid");
        Validator.check("valid");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void handlerSwallowsUserFailureUnderSeparateThreadTimeout() throws Exception {
        mockStatic(Validator.class);
        when(Validator.parse("x")).thenReturn(5);
        assertEquals(5, Validator.parse("x"));
        Validator.check("valid");
        throw new UserFailure(Validator.EXPECTED_PREFIX + "from separate thread");
    }

    @Test
    void controlAssertThrowsUserExceptionsInsideTest() {
        UserFailure unchecked = assertThrows(UserFailure.class, () -> Validator.check(""));
        assertEquals(Validator.EXPECTED_PREFIX + "empty input", unchecked.getMessage());
        CheckedUserFailure checked = assertThrows(CheckedUserFailure.class, () -> Validator.parse("x"));
        assertEquals("not a number: x", checked.getMessage());
        mockStatic(Validator.class);
        doThrow(new UserFailure("stubbed")).when(Validator.class);
        Validator.check("valid");
        assertThrows(UserFailure.class, () -> Validator.check("valid"));
    }
}
