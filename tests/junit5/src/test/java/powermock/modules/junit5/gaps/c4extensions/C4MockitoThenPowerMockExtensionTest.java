package powermock.modules.junit5.gaps.c4extensions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.IdGenerator;
import powermock.modules.junit5.gaps.support.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C4: @ExtendWith({MockitoExtension.class, PowerMockExtension.class}) (Mockito registered first).
 * The @Mock fields must be usable mocks in the test (stub, call, verify with the Mockito the test code uses),
 * together with PowerMock static mocking in the same test.
 */
@ExtendWith({MockitoExtension.class, PowerMockExtension.class})
@PrepareForTest(IdGenerator.class)
class C4MockitoThenPowerMockExtensionTest {

    @Mock
    private UserRepository repository;

    @Test
    void mockFieldIsUsableMock() {
        assertNotNull(repository, "@Mock field must be initialised");
        assertTrue(mockingDetails(repository).isMock());
        when(repository.find(3)).thenReturn("three");
        assertEquals("three", repository.find(3));
        verify(repository).find(3);
    }

    @Test
    void mockFieldAndStaticMockingInSameTest() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("static");
        when(repository.find(4)).thenReturn("four");

        assertEquals("four#static", repository.find(4) + "#" + IdGenerator.next());
        verify(repository).find(4);
        verifyStatic(IdGenerator.class);
        IdGenerator.next();
    }

    @Test
    void controlStaticMockingAloneWithMockitoExtensionPresent() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("only-static");
        assertEquals("only-static", IdGenerator.next());
    }
}
