package org.powermock.core;

import javassist.ClassPool;
import org.junit.Test;
import org.powermock.core.classloader.MockClassLoader;
import org.powermock.core.classloader.javassist.JavassistMockClassLoader;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedClassDefinerTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Test
    public void should_define_class_in_mock_class_loader_so_it_can_be_found_by_name() throws Exception {
        final MockClassLoader mockClassLoader = new JavassistMockClassLoader(new String[0]);
        final String name = uniqueName();

        Class<?> defined = GeneratedClassDefiner.define(name, bytecodeOf(name), mockClassLoader,
                getClass().getProtectionDomain());

        assertThat(defined.getName()).isEqualTo(name);
        assertThat(defined.getClassLoader()).isSameAs(mockClassLoader);
        assertThat(Class.forName(name, false, mockClassLoader)).isSameAs(defined);
    }

    @Test
    public void should_define_class_in_child_of_other_class_loaders() throws Exception {
        final ClassLoader parent = getClass().getClassLoader();
        final String name = uniqueName();

        Class<?> defined = GeneratedClassDefiner.define(name, bytecodeOf(name), parent, getClass().getProtectionDomain());

        assertThat(defined.getName()).isEqualTo(name);
        assertThat(defined.getClassLoader().getParent()).isSameAs(parent);
        assertThat(defined.getProtectionDomain()).isSameAs(getClass().getProtectionDomain());
    }

    @Test
    public void should_create_class_replica_without_reflective_access_to_class_loader() throws Exception {
        Class<Runnable> replica = new ClassReplicaCreator().createClassReplica(Runnable.class);

        assertThat(replica.getName()).startsWith("replica.java.lang.Runnable$$PowerMock");
        assertThat(replica.getClassLoader().getParent()).isSameAs(ClassReplicaCreator.class.getClassLoader());
    }

    private static String uniqueName() {
        return "generated.GeneratedClassDefinerTestClass" + COUNTER.incrementAndGet();
    }

    private static byte[] bytecodeOf(String name) throws Exception {
        return ClassPool.getDefault().makeClass(name).toBytecode();
    }
}
