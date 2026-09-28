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

import org.powermock.core.classloader.MockClassLoader;

import java.security.ProtectionDomain;

/**
 * Defines classes that PowerMock generates at runtime (replicas, concrete subclasses).
 * <p>
 * {@code CtClass#toClass(ClassLoader, ProtectionDomain)} calls the protected
 * {@code ClassLoader#defineClass} reflectively, which the module system forbids on Java 16+
 * unless {@code java.lang} is opened. Instead the class is defined through a class loader that
 * PowerMock owns: the {@link MockClassLoader} itself when the generating code runs inside one,
 * otherwise a fresh child loader of the given loader.
 * </p>
 * <p>
 * Public because PowerMock classes may be loaded by different class loaders (the caller by a
 * {@link MockClassLoader}, this class deferred to the system class loader). For the same reason the
 * signature uses JDK types only: javassist itself may be loaded by both loaders.
 * </p>
 */
public final class GeneratedClassDefiner {

    private GeneratedClassDefiner() {
    }

    public static Class<?> define(String name, byte[] bytes, ClassLoader loader, ProtectionDomain protectionDomain) {
        if (loader instanceof MockClassLoader) {
            return ((MockClassLoader) loader).defineClass(name, protectionDomain, bytes);
        }
        return new DefiningClassLoader(loader).define(name, bytes, protectionDomain);
    }

    private static final class DefiningClassLoader extends ClassLoader {
        DefiningClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes, ProtectionDomain protectionDomain) {
            return defineClass(name, bytes, 0, bytes.length, protectionDomain);
        }
    }
}
