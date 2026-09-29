package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.powermock.core.classloader.ByteCodeFramework;
import org.powermock.core.classloader.MockClassLoader;
import org.powermock.core.classloader.MockClassLoaderBuilder;
import org.powermock.core.classloader.annotations.PrepareEverythingForTest;
import org.powermock.core.classloader.annotations.UseClassPathAdjuster;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;
import org.powermock.tests.utils.impl.PrepareForTestExtractorImpl;
import org.powermock.tests.utils.impl.StaticConstructorSuppressExtractorImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One MockClassLoader per top-level test class, shared by all its {@code @Nested} classes (built from the
 * union of their @PrepareForTest, @PowerMockIgnore, @SuppressStaticInitializationFor), and the "shadow"
 * instances in that class loader that mirror Jupiter's test instances.
 * Cached in the top-level class's ExtensionContext store, so it lives as long as the test class runs.
 */
public class TestClassInClassLoader {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestClassInClassLoader.class);

    private final ClassLoader classLoader;
    private final ObjectTransfer transfer;
    // ponytail: shadows of PER_METHOD instances are kept until the top-level class finishes; fine for test-sized classes
    private final Map<Object, ShadowState> shadows = Collections.synchronizedMap(new IdentityHashMap<Object, ShadowState>());

    private TestClassInClassLoader(Class<?> topLevelClass) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        collectNestedClasses(topLevelClass, classes);
        this.classLoader = createMockClassLoader(topLevelClass, classes);
        new MockPolicyInitializerImpl(topLevelClass).initialize(classLoader);
        this.transfer = new ObjectTransfer(classLoader, shadows);
    }

    private static void collectNestedClasses(Class<?> type, List<Class<?>> result) {
        result.add(type);
        for (Class<?> member : type.getDeclaredClasses()) {
            collectNestedClasses(member, result);
        }
    }

    private static ClassLoader createMockClassLoader(Class<?> topLevelClass, List<Class<?>> classes) {
        Set<String> toModify = new LinkedHashSet<String>();
        Set<String> ignore = new LinkedHashSet<String>();
        boolean everything = false;
        for (Class<?> type : classes) {
            toModify.add(type.getName());
            everything |= type.isAnnotationPresent(PrepareEverythingForTest.class);
            addAll(toModify, new PrepareForTestExtractorImpl().getTestClasses(type));
            addAll(toModify, new StaticConstructorSuppressExtractorImpl().getTestClasses(type));
            addAll(ignore, new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(type));
        }
        if (everything) {
            toModify.clear();
            toModify.add(MockClassLoader.MODIFY_ALL_CLASSES);
        }
        return MockClassLoaderBuilder.create(ByteCodeFramework.getByteCodeFrameworkForTestClass(topLevelClass))
            .forTestClass(topLevelClass)
            .addIgnorePackage(ignore.toArray(new String[0]))
            .addClassesToModify(toModify.toArray(new String[0]))
            .addClassPathAdjuster(topLevelClass.getAnnotation(UseClassPathAdjuster.class))
            .build();
    }

    private static void addAll(Set<String> target, String[] values) {
        if (values != null) {
            target.addAll(Arrays.asList(values));
        }
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        Class<?> topLevelClass = context.getRequiredTestClass();
        while (topLevelClass.getEnclosingClass() != null) {
            topLevelClass = topLevelClass.getEnclosingClass();
        }
        ExtensionContext topLevelContext = context;
        while (topLevelContext.getParent().isPresent() && topLevelContext.getParent().get().getTestClass().isPresent()) {
            topLevelContext = topLevelContext.getParent().get();
        }
        return topLevelContext.getStore(NAMESPACE).getOrComputeIfAbsent(
            topLevelClass, TestClassInClassLoader::new, TestClassInClassLoader.class);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ObjectTransfer getTransfer() {
        return transfer;
    }

    /** Creates the shadow of a test instance Jupiter just constructed, with the same (transferred) constructor arguments. */
    public void createShadowInstance(Object originalInstance, Constructor<?> originalConstructor, List<Object> arguments) throws Exception {
        final Constructor<?> constructor = (Constructor<?>) findExecutable(originalConstructor);
        final Object[] args = transfer.toMockClassLoader(arguments, constructor.getParameterTypes());
        Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(args));
        shadows.put(originalInstance, new ShadowState(originalInstance, shadow));
    }

    public ShadowState getShadow(Object originalInstance) {
        ShadowState shadow = shadows.get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }

    public Executable findExecutable(Executable original) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> declaringClass = Class.forName(original.getDeclaringClass().getName(), false, classLoader);
        Class<?>[] originalTypes = original.getParameterTypes();
        Class<?>[] types = new Class<?>[originalTypes.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = originalTypes[i].isPrimitive() ? originalTypes[i]
                : Class.forName(originalTypes[i].getName(), false, classLoader);
        }
        Executable executable = original instanceof Constructor
            ? declaringClass.getDeclaredConstructor(types)
            : declaringClass.getDeclaredMethod(original.getName(), types);
        executable.setAccessible(true);
        return executable;
    }
}
