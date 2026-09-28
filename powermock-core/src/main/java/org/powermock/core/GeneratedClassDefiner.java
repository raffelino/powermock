/*
 * Copyright 2011 the original author or authors.
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
 * Defines classes generated with javassist ({@link ClassReplicaCreator}, {@link ConcreteClassGenerator}) in the
 * class loader of a host class. Internal PowerMock class.
 * <p>
 * The classic way, {@code CtClass.toClass(ClassLoader, ProtectionDomain)}, injects the class into an existing class
 * loader by calling the protected {@code ClassLoader.defineClass} via deep reflection. Java 16+ denies that unless
 * {@code java.lang} is opened to PowerMock. Therefore:
 * <ol>
 * <li>If the loader is a {@link MockClassLoader} (the normal case inside a PowerMock test), its public
 * {@code defineClass} is used, no reflection needed.</li>
 * <li>Otherwise javassist's reflective injection is tried as before.</li>
 * <li>If the JVM denies it, the class is defined in a small child class loader of the requested loader. The generated
 * classes live in their own packages ({@code replica.*}, {@code subclass.*}) and need no package-private access, so
 * all type references resolve exactly as they would in the parent.</li>
 * </ol>
 * The signature only uses JDK types on purpose: callers may be loaded by a {@link MockClassLoader} that has its own
 * copy of javassist, while this class is always loaded by the loader of the {@code org.powermock.core} package.
 */
public final class GeneratedClassDefiner {

    private GeneratedClassDefiner() {
    }

    public static Class<?> define(String className, byte[] bytecode, Class<?> host) throws Exception {
        final ClassLoader loader = host.getClassLoader();
        final ProtectionDomain protectionDomain = host.getProtectionDomain();
        if (loader instanceof MockClassLoader) {
            return ((MockClassLoader) loader).defineClass(className, protectionDomain, bytecode);
        }
        try {
            return javassist.util.proxy.DefineClassHelper.toClass(className, null, loader, protectionDomain, bytecode);
        } catch (Exception injectionDenied) {
            // Typically java.lang.reflect.InaccessibleObjectException on Java 16+ when java.lang is not opened.
            return new ChildClassLoader(loader).define(className, bytecode, protectionDomain);
        } catch (IllegalAccessError injectionDenied) {
            // Thrown by some of javassist's JDK specific injection strategies.
            return new ChildClassLoader(loader).define(className, bytecode, protectionDomain);
        }
    }

    private static final class ChildClassLoader extends ClassLoader {
        private ChildClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String name, byte[] bytecode, ProtectionDomain protectionDomain) {
            return defineClass(name, bytecode, 0, bytecode.length, protectionDomain);
        }
    }
}
