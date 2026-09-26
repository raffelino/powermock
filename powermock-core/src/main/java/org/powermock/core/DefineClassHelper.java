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

import java.security.ProtectionDomain;

/**
 * Helper to define classes without using reflection's setAccessible on
 * ClassLoader.defineClass, which is inaccessible on JDK 17+.
 *
 * Uses a helper ClassLoader subclass that can call defineClass directly.
 */
public class DefineClassHelper {

    /**
     * Define a class from bytecode without using reflection's setAccessible.
     *
     * @param name           The binary name of the class
     * @param b              The class file bytes
     * @param off            The start offset in the class file
     * @param len            The length of the class file
     * @param loader         The ClassLoader context for loading
     * @param protectionDomain The ProtectionDomain, may be null
     * @return The defined class
     */
    public static Class<?> defineClass(
            String name,
            byte[] b,
            int off,
            int len,
            ClassLoader loader,
            ProtectionDomain protectionDomain
    ) {
        // If loader is null, use the system classloader
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }

        // Create a helper ClassLoader with the given loader as parent
        // The helper can call defineClass directly without reflection's setAccessible
        ClassLoaderHelper helper = new ClassLoaderHelper(loader);
        return helper.doDefineClass(name, b, off, len, protectionDomain);
    }

    /**
     * Helper that extends ClassLoader and can call defineClass directly.
     */
    private static class ClassLoaderHelper extends ClassLoader {
        ClassLoaderHelper(ClassLoader parent) {
            super(parent);
        }

        /**
         * Call the protected defineClass method directly (not via reflection).
         */
        Class<?> doDefineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
            return defineClass(name, b, off, len, protectionDomain);
        }
    }
}
