package powermock.modules.junit5.gaps.c1annotations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.AuditLog;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;
import powermock.modules.junit5.gaps.support.UserRepository;
import powermock.modules.junit5.gaps.support.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/** C1: @InjectMocks object under test with @Mock collaborators (one of them final), plus mockStatic and whenNew. */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Greeter.class, IdGenerator.class, UserService.class})
class C1InjectMocksTest {

    @Mock
    private UserRepository repository;

    @Mock
    private Greeter greeter;

    @InjectMocks
    private UserService service;

    @Test
    void mocksAreInjectedIntoObjectUnderTest() {
        assertNotNull(service, "@InjectMocks must create the object under test");
        assertSame(repository, service.getRepository());
        assertSame(greeter, service.getGreeter());

        when(repository.find(1)).thenReturn("bob");
        when(greeter.greet("bob")).thenReturn("Hi bob");
        assertEquals("Hi bob", service.describe(1));
        verify(greeter).greet("bob");
    }

    @Test
    void injectedMocksCombinedWithStaticMocking() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("id-9");
        when(repository.find(9)).thenReturn("carol");

        assertEquals("carol#id-9", service.describeWithId(9));
    }

    @Test
    void injectedMocksCombinedWithWhenNew() throws Exception {
        AuditLog auditLog = mock(AuditLog.class);
        whenNew(AuditLog.class).withNoArguments().thenReturn(auditLog);
        when(auditLog.record("login")).thenReturn("mocked-audit");

        assertEquals("mocked-audit", service.audit("login"));
        verifyNew(AuditLog.class).withNoArguments();
    }
}
