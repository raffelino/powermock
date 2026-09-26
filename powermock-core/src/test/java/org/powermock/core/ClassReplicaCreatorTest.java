package org.powermock.core;

import org.junit.Test;

import java.util.AbstractList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Must pass on JDK 16+ with java.lang closed: generated classes are defined without reflective ClassLoader.defineClass.
public class ClassReplicaCreatorTest {

    @Test
    public void should_create_replica_of_final_system_class() {
        Class<String> replica = new ClassReplicaCreator().createClassReplica(String.class);
        assertTrue(replica.getName().startsWith("replica.java.lang.String$$PowerMock"));
        assertEquals(ClassReplicaCreator.class.getClassLoader(), replica.getClassLoader().getParent());
    }

    @Test
    public void should_create_concrete_subclass_of_abstract_class() {
        Class<?> concrete = new ConcreteClassGenerator().createConcreteSubClass(AbstractList.class);
        assertEquals(AbstractList.class, concrete.getSuperclass());
    }
}
