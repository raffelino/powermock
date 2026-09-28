package org.powermock.core.classloader;

import org.junit.Test;
import org.powermock.core.classloader.javassist.JavassistMockClassLoader;
import org.powermock.core.test.MockClassLoaderFactory;

import javax.crypto.Cipher;
import java.util.Arrays;
import java.util.HashSet;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * Classes of named JDK modules that are not prepared for test must keep their module membership (Java 9+),
 * otherwise e.g. {@code javax.crypto.Cipher} fails with {@code IllegalAccessError} on {@code sun.security.util}.
 */
public class MockClassLoaderJdkModuleTest {

    private static final boolean JAVA_9_OR_LATER = hasModules();

    private final MockClassLoaderFactory mockClassLoaderFactory = new MockClassLoaderFactory(JavassistMockClassLoader.class);

    @Test
    public void unmodified_class_of_jdk_module_is_the_original_class_on_java_9_or_later() throws Exception {
        MockClassLoader mockClassLoader = mockClassLoaderFactory.getInstance(new String[]{"powermock.test.support.ClassForMockClassLoaderTestCase"});

        Class<?> clazz = Class.forName("javax.crypto.Cipher", false, mockClassLoader);

        if (JAVA_9_OR_LATER) {
            assertThat(clazz).isSameAs(Cipher.class);
        } else {
            assertThat(clazz.getClassLoader()).isSameAs(mockClassLoader);
        }
    }

    @Test
    public void unmodified_class_of_jdk_module_with_prepared_class_is_loaded_by_mock_class_loader() throws Exception {
        MockClassLoader mockClassLoader = mockClassLoaderFactory.getInstance(new String[]{"javax.naming.InitialContext"});

        Class<?> clazz = Class.forName("javax.naming.NameImpl", false, mockClassLoader);

        assertThat(clazz.getClassLoader()).isSameAs(mockClassLoader);
    }

    @Test
    public void may_modify_classes_in_packages_of_prepared_classes() {
        MockClassLoaderConfiguration configuration = new MockClassLoaderConfiguration();
        configuration.addClassesToModify("javax.naming.InitialContext");

        assertThat(configuration.mayModifyClassesInPackages(packages("javax.naming", "com.sun.naming.internal"))).isTrue();
        assertThat(configuration.mayModifyClassesInPackages(packages("javax.crypto", "sun.security.util"))).isFalse();
    }

    @Test
    public void may_modify_classes_in_packages_matched_by_wildcards() {
        MockClassLoaderConfiguration configuration = new MockClassLoaderConfiguration();
        configuration.addClassesToModify("javax.naming.*");

        assertThat(configuration.mayModifyClassesInPackages(packages("javax.naming.spi"))).isTrue();
        assertThat(configuration.mayModifyClassesInPackages(packages("javax.crypto"))).isFalse();

        configuration.addClassesToModify("*Test");
        assertThat(configuration.mayModifyClassesInPackages(packages("javax.crypto"))).isTrue();
    }

    private static HashSet<String> packages(String... packages) {
        return new HashSet<String>(Arrays.asList(packages));
    }

    private static boolean hasModules() {
        try {
            Class.class.getMethod("getModule");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
