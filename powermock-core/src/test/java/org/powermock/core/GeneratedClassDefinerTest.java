package org.powermock.core;

import org.junit.Test;

import java.util.AbstractList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generated classes must be definable without reflective access to
 * {@code ClassLoader#defineClass} (JDK 16+ without opening java.lang).
 */
public class GeneratedClassDefinerTest {

    @Test
    public void should_create_class_replica_of_final_system_class() {
        Class<String> replica = new ClassReplicaCreator().createClassReplica(String.class);

        assertThat(replica.getName()).startsWith("replica.java.lang.String$$PowerMock");
        assertThat(replica.getClassLoader()).isNotNull();
    }

    @Test
    public void should_create_instance_replica_of_final_system_class() {
        Class<UUID> replica = new ClassReplicaCreator().createInstanceReplica(UUID.randomUUID());

        assertThat(replica.getName()).startsWith("replica.java.util.UUID$$PowerMock");
    }

    @Test
    public void should_create_concrete_subclass_of_abstract_class() {
        Class<?> subclass = new ConcreteClassGenerator().createConcreteSubClass(AbstractList.class);

        assertThat(subclass).isNotNull();
        assertThat(AbstractList.class.isAssignableFrom(subclass)).isTrue();
    }
}
