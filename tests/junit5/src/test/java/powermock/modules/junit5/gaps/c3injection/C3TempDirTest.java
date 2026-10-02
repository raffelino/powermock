package powermock.modules.junit5.gaps.c3injection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.IdGenerator;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C3: @TempDir fields and parameters. The field the test uses must be the directory Jupiter created for
 * this test: an AfterEachCallback reads the field from Jupiter's test instance and must find the file
 * the test wrote there.
 */
@ExtendWith({PowerMockExtension.class, C3TempDirTest.TempDirFileCheck.class})
@PrepareForTest(IdGenerator.class)
class C3TempDirTest {

    static final String FILE = "written-by-test.txt";

    @TempDir
    Path tempDir;

    @Test
    void tempDirFieldIsInjectedEmptyDirectory() throws IOException {
        assertNotNull(tempDir, "@TempDir field must be injected");
        assertTrue(Files.isDirectory(tempDir));
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
        writeMarker();
    }

    @Test
    void tempDirFieldIsTheDirectoryJupiterManages() throws IOException {
        assertNotNull(tempDir, "@TempDir field must be injected");
        writeMarker(); // TempDirFileCheck verifies this file exists in the field value of Jupiter's instance
    }

    @Test
    void tempDirFieldWithStaticMocking() throws IOException {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("tmp-static");
        assertNotNull(tempDir, "@TempDir field must be injected");
        Files.write(tempDir.resolve("id.txt"), Collections.singletonList(IdGenerator.next()), StandardCharsets.UTF_8);
        assertEquals(Collections.singletonList("tmp-static"), Files.readAllLines(tempDir.resolve("id.txt"), StandardCharsets.UTF_8));
        writeMarker();
    }

    @Test
    void controlTempDirParameter(@TempDir Path parameterDir) throws IOException {
        assertTrue(Files.isDirectory(parameterDir));
        Files.write(parameterDir.resolve("p.txt"), Collections.singletonList("p"), StandardCharsets.UTF_8);
        assertTrue(Files.exists(parameterDir.resolve("p.txt")));
        if (tempDir != null) {
            writeMarker();
        }
    }

    private void writeMarker() throws IOException {
        Files.write(tempDir.resolve(FILE), Collections.singletonList("x"), StandardCharsets.UTF_8);
    }

    /** Reads the @TempDir field of Jupiter's test instance after each test (before Jupiter deletes it). */
    static class TempDirFileCheck implements AfterEachCallback {
        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            if (!context.getRequiredTestMethod().getName().startsWith("tempDirField")) {
                return;
            }
            Object instance = context.getRequiredTestInstance();
            Field field = instance.getClass().getDeclaredField("tempDir");
            field.setAccessible(true);
            Path dir = (Path) field.get(instance);
            if (dir == null || !Files.exists(dir.resolve(FILE))) {
                throw new AssertionError("the test's @TempDir file is not in the @TempDir of Jupiter's test instance: " + dir);
            }
        }
    }
}
