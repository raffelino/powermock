package org.powermock.api.mockito.internal.invocation;

import org.junit.Test;
import org.mockito.exceptions.verification.TooFewActualInvocations;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class InvocationControlAssertionErrorTest {

    @Test
    public void with_message_keeps_type_and_stack_trace() {
        final TooFewActualInvocations original = new TooFewActualInvocations("original");
        final StackTraceElement[] stackTrace = original.getStackTrace();

        final TooFewActualInvocations updated = InvocationControlAssertionError.withMessage(original, "updated");

        assertThat(updated).isInstanceOf(TooFewActualInvocations.class);
        assertThat(updated.getMessage()).isEqualTo("updated");
        assertThat(updated.getStackTrace()).isEqualTo(stackTrace);
    }

    @Test
    public void with_message_works_for_plain_assertion_error() {
        final AssertionError updated = InvocationControlAssertionError.withMessage(new AssertionError("original"), "updated");

        assertThat(updated.getMessage()).isEqualTo("updated");
    }
}
