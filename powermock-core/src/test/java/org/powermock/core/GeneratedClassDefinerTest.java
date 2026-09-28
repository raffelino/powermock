package org.powermock.core;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class GeneratedClassDefinerTest {

    @Test
    public void defines_generated_class_visible_to_the_class_loader_of_the_host() throws Exception {
        final String className = "replica.org.powermock.core.GeneratedClassDefinerTest$$Generated";
        final ClassPool classPool = ClassPool.getDefault();
        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(Callable.class.getName()));
        ctClass.addMethod(CtNewMethod.make("public Object call() { return \"generated\"; }", ctClass));

        final Class<?> clazz = GeneratedClassDefiner.define(className, ctClass.toBytecode(), getClass());

        assertThat(clazz.getName()).isEqualTo(className);
        assertThat(((Callable<?>) clazz.newInstance()).call()).isEqualTo("generated");
    }

    @Test
    public void class_replica_of_system_class_can_be_created() {
        final Class<StringBuilder> replica = new ClassReplicaCreator().createClassReplica(StringBuilder.class);

        assertThat(replica.getName()).startsWith("replica.java.lang.StringBuilder$$PowerMock");
    }
}
