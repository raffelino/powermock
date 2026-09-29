package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.powermock.core.classloader.ByteCodeFramework;
import org.powermock.core.classloader.MockClassLoaderBuilder;
import org.powermock.core.classloader.annotations.PrepareEverythingForTest;
import org.powermock.core.classloader.annotations.UseClassPathAdjuster;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;
import org.powermock.tests.utils.impl.PrepareForTestExtractorImpl;
import org.powermock.tests.utils.impl.StaticConstructorSuppressExtractorImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One MockClassLoader per top-level test class, shared by all its @Nested classes. It prepares the union
 * of what the top-level class and all its nested classes ask for (@PrepareForTest, @SuppressStaticInitializationFor,
 * @PowerMockIgnore). Types of @RegisterExtension fields are not reloaded, so Jupiter and the test code share the
 * extension object. Also creates and finds the shadow instances mirroring Jupiter's test instances.
 */
public class TestClassInClassLoader {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestClassInClassLoader.class);

    private final ClassLoader classLoader;

    private TestClassInClassLoader(Class<?> topLevelClass) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        collect(topLevelClass, classes);
        Set<String> toModify = new LinkedHashSet<String>();
        Set<String> toIgnore = new LinkedHashSet<String>();
        boolean everything = false;
        for (Class<?> type : classes) {
            everything |= type.isAnnotationPresent(PrepareEverythingForTest.class);
            addAll(toModify, new PrepareForTestExtractorImpl().getTestClasses(type));
            addAll(toModify, new StaticConstructorSuppressExtractorImpl().getTestClasses(type));
            addAll(toIgnore, new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(type));
            toModify.add(type.getName());
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(RegisterExtension.class)) {
                        toIgnore.add(field.getType().getName());
                    }
                }
            }
        }
        if (everything) {
            toModify.add(org.powermock.core.classloader.MockClassLoader.MODIFY_ALL_CLASSES);
        }
        this.classLoader = MockClassLoaderBuilder.create(ByteCodeFramework.getByteCodeFrameworkForTestClass(topLevelClass))
            .forTestClass(topLevelClass)
            .addIgnorePackage(toIgnore.toArray(new String[0]))
            .addClassesToModify(toModify.toArray(new String[0]))
            .addClassPathAdjuster(topLevelClass.getAnnotation(UseClassPathAdjuster.class))
            .build();
        for (Class<?> type : classes) {
            new MockPolicyInitializerImpl(type).initialize(classLoader);
        }
    }

    private static void collect(Class<?> type, List<Class<?>> classes) {
        classes.add(type);
        for (Class<?> member : type.getDeclaredClasses()) {
            collect(member, classes);
        }
    }

    private static void addAll(Set<String> set, String[] values) {
        if (values != null) {
            set.addAll(Arrays.asList(values));
        }
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        Class<?> topLevelClass = context.getRequiredTestClass();
        while (topLevelClass.getEnclosingClass() != null) {
            topLevelClass = topLevelClass.getEnclosingClass();
        }
        ExtensionContext owner = context;
        for (ExtensionContext c = context; c != null; c = c.getParent().orElse(null)) {
            if (c.getTestClass().orElse(null) == topLevelClass && !c.getTestMethod().isPresent()) {
                owner = c;
                break;
            }
        }
        final Class<?> top = topLevelClass;
        return owner.getStore(NAMESPACE).getOrComputeIfAbsent(
            "loader:" + top.getName(), k -> new TestClassInClassLoader(top), TestClassInClassLoader.class);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ClassLoader getOriginalClassLoader(ExtensionContext context) {
        return context.getRequiredTestClass().getClassLoader();
    }

    public Class<?> loadInMockClassLoader(Class<?> type) {
        return ObjectTransfer.targetClass(type, classLoader);
    }

    /** Creates the shadow of a test instance Jupiter just constructed, with the same (converted) arguments. */
    public ShadowInstance createShadowInstance(Object originalInstance, Constructor<?> originalConstructor,
                                              List<Object> arguments, ExtensionContext context) throws Exception {
        Class<?> shadowClass = loadInMockClassLoader(originalConstructor.getDeclaringClass());
        Class<?>[] parameterTypes = originalConstructor.getParameterTypes();
        Class<?>[] shadowTypes = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            shadowTypes[i] = loadInMockClassLoader(parameterTypes[i]);
        }
        final Constructor<?> constructor = shadowClass.getDeclaredConstructor(shadowTypes);
        constructor.setAccessible(true);
        final Object[] args = new Object[arguments.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = ObjectTransfer.transfer(arguments.get(i), classLoader, counterparts(context));
        }
        Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(args));
        AnnotationSupport.injectMocks(classLoader, shadow);
        ShadowInstance instance = new ShadowInstance(originalInstance, shadow);
        // Store keys use equals(); test classes don't override it, so this is identity.
        context.getStore(NAMESPACE).put(originalInstance, instance);
        return instance;
    }

    public ShadowInstance getShadowInstance(Object originalInstance, ExtensionContext context) {
        ShadowInstance shadow = context.getStore(NAMESPACE).get(originalInstance, ShadowInstance.class);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }

    /** Test instances already have a counterpart: their shadow. */
    public static ObjectTransfer.KnownCounterparts counterparts(final ExtensionContext context) {
        return source -> {
            Object found = context.getStore(NAMESPACE).get(source);
            return found instanceof ShadowInstance ? ((ShadowInstance) found).getShadow() : null;
        };
    }

    /** Reverse direction: shadows map back to their original test instance. */
    public static ObjectTransfer.KnownCounterparts reverseCounterparts(final ShadowInstance instance) {
        return source -> source == instance.getShadow() ? instance.getOriginal() : null;
    }
}
