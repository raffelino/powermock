package powermock.modules.junit5.gaps.c3injection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Account;
import powermock.modules.junit5.gaps.support.AccountResolver;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C3: a test class whose only constructor takes parameters resolved by Jupiter (TestInfo) and by a
 * custom ParameterResolver (a user-class object), in a class that also uses static and final mocking.
 */
@ExtendWith({PowerMockExtension.class, AccountResolver.class})
@PrepareForTest({IdGenerator.class, Greeter.class, Account.class})
class C3ConstructorInjectionTest {

    private final TestInfo constructorInfo;
    private final Account account;

    C3ConstructorInjectionTest(TestInfo constructorInfo, Account account) {
        this.constructorInfo = constructorInfo;
        this.account = account;
    }

    @Test
    @DisplayName("constructor TestInfo is the one Jupiter resolved")
    void constructorTestInfoIsResolvedByJupiter(TestInfo methodInfo) {
        assertNotNull(constructorInfo);
        assertEquals(C3ConstructorInjectionTest.class.getName(), constructorInfo.getTestClass().get().getName());
        // Jupiter 5.14 (default extension context scope) gives the class context to constructors;
        // the method context is also accepted.
        String displayName = constructorInfo.getDisplayName();
        assertTrue(displayName.equals("C3ConstructorInjectionTest") || displayName.equals(methodInfo.getDisplayName()),
            "unexpected constructor TestInfo display name: " + displayName);
    }

    @Test
    void constructorInjectedUserObjectIsUsableWithStaticMocking() {
        assertNotNull(account);
        assertEquals(AccountResolver.OWNER, account.owner());
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("ctor-static");
        assertEquals("alice#ctor-static", account.idLabel(),
            "the injected Account must use the prepared (mocked) IdGenerator");
    }

    @Test
    void constructorInjectionCoexistsWithFinalMocking() {
        Greeter greeter = mock(Greeter.class);
        when(greeter.greet(account.owner())).thenReturn("final-mocked");
        assertEquals("final-mocked", greeter.greet("alice"));
        assertEquals("account:alice", account.label());
    }
}
