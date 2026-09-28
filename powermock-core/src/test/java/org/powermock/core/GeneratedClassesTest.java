/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powermock.core;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Generating replica and concrete classes must work without opening
 * {@code java.lang} on Java 16+ (no reflective {@code ClassLoader.defineClass}).
 */
public class GeneratedClassesTest {

    public abstract static class AbstractGreeter {
        public abstract String greet(String name);

        public String hello() {
            return "hello";
        }
    }

    @Test
    public void creates_class_replica_of_system_class() throws Exception {
        Class<String> replica = new ClassReplicaCreator().createClassReplica(String.class);

        assertTrue(replica.getName().startsWith("replica.java.lang.String$$PowerMock"));
    }

    @Test
    public void creates_instance_replica_of_system_class() throws Exception {
        Class<UUID> replica = new ClassReplicaCreator().createInstanceReplica(UUID.randomUUID());

        assertTrue(replica.getName().startsWith("replica.java.util.UUID$$PowerMock"));
        assertNotNull(replica.getDeclaredMethod("toString"));
        assertNotNull(replica.getDeclaredConstructor(long.class, long.class));
    }

    @Test
    public void creates_concrete_subclass_of_abstract_class() throws Exception {
        Class<?> concrete = new ConcreteClassGenerator().createConcreteSubClass(AbstractGreeter.class);

        assertTrue(concrete.getName().startsWith("subclass." + AbstractGreeter.class.getName() + "$$PowerMock"));
        assertEquals(AbstractGreeter.class, concrete.getSuperclass());
        AbstractGreeter greeter = (AbstractGreeter) concrete.newInstance();
        assertEquals("hello", greeter.hello());
    }
}
