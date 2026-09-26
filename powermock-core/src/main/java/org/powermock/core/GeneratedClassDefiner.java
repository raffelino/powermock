package org.powermock.core;

import java.security.ProtectionDomain;

/**
 * Defines generated classes (replicas/subclasses in their own packages) in a child class loader of
 * {@code parent}. Unlike javassist's {@code CtClass.toClass(ClassLoader, ProtectionDomain)} this needs no
 * reflective access to {@code ClassLoader.defineClass}, which JDK 16+ denies unless java.lang is opened.
 * Only JDK types in the signature, so it links no matter which loader loaded the caller.
 */
public class GeneratedClassDefiner {

    public static Class<?> define(String name, byte[] bytes, ClassLoader parent, ProtectionDomain domain) {
        return new ChildLoader(parent).define(name, bytes, domain);
    }

    private static class ChildLoader extends ClassLoader {
        ChildLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes, ProtectionDomain domain) {
            return defineClass(name, bytes, 0, bytes.length, domain);
        }
    }
}
