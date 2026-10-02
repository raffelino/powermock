package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.powermock.core.classloader.ByteCodeFramework;
import org.powermock.core.classloader.MockClassLoaderBuilder;
import org.powermock.core.classloader.annotations.PrepareEverythingForTest;
import org.powermock.core.classloader.annotations.UseClassPathAdjuster;
import org.powermock.core.classloader.MockClassLoader;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;
import org.powermock.tests.utils.impl.PrepareForTestExtractorImpl;
import org.powermock.tests.utils.impl.StaticConstructorSuppressExtractorImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One MockClassLoader per top-level test class (shared by all its @Nested classes; it prepares the union of their
 * @PrepareForTest classes), and the "shadow" instances mirroring Jupiter's test instances in that loader.
 * Cached in the top-level class's ExtensionContext store, so it lives as long as the test class runs.
 */
public class TestClassInClassLoader {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestClassInClassLoader.class);

    private final ClassLoader classLoader;
    private final Map<Object, Object> toShadow = Collections.synchronizedMap(new IdentityHashMap<Object, Object>());
    private final Map<Object, Object> toOriginal = Collections.synchronizedMap(new IdentityHashMap<Object, Object>());
    private final FieldSync fieldSync = new FieldSync(this);
    private final List<Object[]> retainedInstanceMocks = new ArrayList<Object[]>();

    private TestClassInClassLoader(Class<?> topLevelClass) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        collectNested(topLevelClass, classes);
        Set<String> ignore = new LinkedHashSet<String>();
        Set<String> modify = new LinkedHashSet<String>();
        boolean everything = false;
        for (Class<?> c : classes) {
            Collections.addAll(ignore, new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(c));
            modify.add(c.getName());
            String[] prepared = new PrepareForTestExtractorImpl().getTestClasses(c);
            if (prepared != null) {
                Collections.addAll(modify, prepared);
            }
            String[] suppressed = new StaticConstructorSuppressExtractorImpl().getTestClasses(c);
            if (suppressed != null) {
                Collections.addAll(modify, suppressed);
            }
            everything |= c.isAnnotationPresent(PrepareEverythingForTest.class);
            // Extensions registered via @RegisterExtension must be the very objects Jupiter calls, so their
            // classes are shared with the application class loader.
            for (Class<?> h = c; h != null && h != Object.class; h = h.getSuperclass()) {
                for (Field f : h.getDeclaredFields()) {
                    if (f.isAnnotationPresent(RegisterExtension.class)) {
                        ignore.add(f.getType().getName());
                    }
                }
            }
        }
        if (everything) {
            modify.add(MockClassLoader.MODIFY_ALL_CLASSES);
        }
        MockClassLoader loader = MockClassLoaderBuilder.create(ByteCodeFramework.getByteCodeFrameworkForTestClass(topLevelClass))
            .forTestClass(topLevelClass)
            .addIgnorePackage(ignore.toArray(new String[0]))
            .addClassesToModify(modify.toArray(new String[0]))
            .addClassPathAdjuster(topLevelClass.getAnnotation(UseClassPathAdjuster.class))
            .build();
        for (Class<?> c : classes) {
            new MockPolicyInitializerImpl(c).initialize(loader);
        }
        this.classLoader = loader;
    }

    private static void collectNested(Class<?> c, List<Class<?>> result) {
        result.add(c);
        for (Class<?> member : c.getDeclaredClasses()) {
            if (member.isAnnotationPresent(Nested.class)) {
                collectNested(member, result);
            }
        }
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        Class<?> topLevel = testClass(context);
        while (topLevel.getEnclosingClass() != null && !java.lang.reflect.Modifier.isStatic(topLevel.getModifiers())) {
            topLevel = topLevel.getEnclosingClass();
        }
        ExtensionContext storeContext = context;
        while (storeContext.getParent().isPresent() && storeContext.getParent().get().getParent().isPresent()) {
            storeContext = storeContext.getParent().get();
        }
        final Class<?> key = topLevel;
        return storeContext.getStore(NAMESPACE).getOrComputeIfAbsent(
            key, TestClassInClassLoader::new, TestClassInClassLoader.class);
    }

    private static Class<?> testClass(ExtensionContext context) {
        for (ExtensionContext c = context; c != null; c = c.getParent().orElse(null)) {
            if (c.getTestClass().isPresent()) {
                return c.getTestClass().get();
            }
        }
        return context.getRequiredTestClass();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    FieldSync fieldSync() {
        return fieldSync;
    }

    /** Transfers objects (arguments, field values) into the MockClassLoader, deep-copying user objects. */
    Object toMockClassLoader(Object value) {
        Object result = new CrossLoader(classLoader, toShadow, true).transfer(value);
        return result == CrossLoader.NOT_SHAREABLE ? value : result;
    }

    CrossLoader sharingToMockClassLoader() {
        return new CrossLoader(classLoader, toShadow, true);
    }

    CrossLoader sharingToApplication(ClassLoader applicationLoader) {
        return new CrossLoader(applicationLoader, toOriginal, false);
    }

    public Throwable translate(Throwable throwable, ClassLoader applicationLoader) {
        return toApplication(throwable, applicationLoader);
    }

    Throwable toApplication(Throwable throwable, ClassLoader applicationLoader) {
        return CrossLoader.toApplication(throwable, applicationLoader, toOriginal);
    }

    public Object createShadowInstance(Object originalInstance, Constructor<?> originalConstructor, Object[] arguments)
        throws Exception {
        Class<?> shadowClass = Class.forName(originalConstructor.getDeclaringClass().getName(), false, classLoader);
        Class<?>[] types = MockClassLoaderInvoker.mapTypes(classLoader, originalConstructor.getParameterTypes());
        final Constructor<?> constructor = shadowClass.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        final Object[] args = new Object[arguments.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = toMockClassLoader(arguments[i]);
        }
        Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> constructor.newInstance(args));
        toShadow.put(originalInstance, shadow);
        toOriginal.put(shadow, originalInstance);
        AnnotationMocks.inject(shadow, classLoader);
        return shadow;
    }

    public Object getShadowInstance(Object originalInstance) {
        Object shadow = toShadow.get(originalInstance);
        if (shadow == null) {
            throw new IllegalStateException("No MockClassLoader instance for " + originalInstance);
        }
        return shadow;
    }

    /** PER_CLASS: PowerMock instance state created in @BeforeAll survives the per-test reset. */
    public synchronized void retainInstanceMocksAfterBeforeAll() {
        retainedInstanceMocks.clear();
        retainedInstanceMocks.addAll(PowerMockStateCleaner.instanceMocks(classLoader));
    }

    public synchronized void clearState() {
        PowerMockStateCleaner.clear(classLoader, retainedInstanceMocks);
    }
}
