package powermock.modules.junit5.gaps.c3injection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Account;
import powermock.modules.junit5.gaps.support.AccountResolver;
import powermock.modules.junit5.gaps.support.IdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C3: test and lifecycle method parameters from Jupiter (TestInfo, TestReporter) and from a custom
 * ParameterResolver supplying a user-class object that must behave like any object of a prepared class.
 */
@ExtendWith({PowerMockExtension.class, AccountResolver.class})
@PrepareForTest({IdGenerator.class, Account.class})
class C3MethodParameterInjectionTest {

    private Account accountFromBeforeEach;
    private String beforeEachDisplayName;

    @BeforeEach
    void setUp(Account account, TestInfo info) {
        accountFromBeforeEach = account;
        beforeEachDisplayName = info.getDisplayName();
    }

    @Test
    void resolvedUserObjectAsTestParameterUsesMockedStatic(Account account) {
        assertEquals(AccountResolver.OWNER, account.owner());
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("param-static");
        assertEquals("alice#param-static", account.idLabel());
    }

    @Test
    void resolvedUserObjectFromBeforeEachIsUsableInTest() {
        assertNotNull(accountFromBeforeEach, "@BeforeEach parameter from the custom resolver");
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("before-each-static");
        assertEquals("alice#before-each-static", accountFromBeforeEach.idLabel());
    }

    @Test
    void resolvedUserObjectCanBeSpiedAndFinalMethodStubbed(Account account) throws Exception {
        Account spied = spy(account);
        doReturn("stubbed-label").when(spied).label();
        assertEquals("stubbed-label", spied.label());
        assertEquals(AccountResolver.OWNER, spied.owner());
    }

    @Test
    @DisplayName("jupiter params")
    void controlJupiterParametersAreTheCurrentOnes(TestInfo info, TestReporter reporter) {
        assertEquals("jupiter params", info.getDisplayName());
        assertEquals("jupiter params", beforeEachDisplayName);
        assertEquals("controlJupiterParametersAreTheCurrentOnes", info.getTestMethod().get().getName());
        reporter.publishEntry("c3", "reported");
    }
}
