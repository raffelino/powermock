package org.powermock.modules.junit5.internal;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.powermock.core.classloader.ByteCodeFramework;
import org.powermock.core.classloader.MockClassLoaderBuilder;
import org.powermock.core.classloader.MockClassLoaderFactory;
import org.powermock.tests.utils.impl.MockPolicyInitializerImpl;
import org.powermock.tests.utils.impl.PowerMockIgnorePackagesExtractorImpl;
import org.powermock.tests.utils.impl.PrepareForTestExtractorImpl;
import org.powermock.tests.utils.impl.StaticConstructorSuppressExtractorImpl;
import org.powermock.reflect.Whitebox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One MockClassLoader per test class (built from @PrepareForTest, @PowerMockIgnore,
 * @SuppressStaticInitializationFor, mock policies ... exactly as the TestNG module does), the test
 * class loaded by it, and the shadow instances mirroring Jupiter's test instances.
 * <p>
 * A {@code @Nested} class gets its own MockClassLoader that prepares the classes of all enclosing classes
 * plus its own; the enclosing instances get shadows in that loader too, so the inner shadow's {@code this$0}
 * is a shadow of the same world.
 * <p>
 * Jupiter and other extensions work on the original instances (constructor/field injection, post
 * processors, callbacks); the test code runs on the shadows. Field state is therefore synchronised:
 * before every redirected invocation, fields changed on the original are copied (converted) to the shadow;
 * afterwards, values that exist unchanged in both worlds (JDK types, deferred classes) are copied back.
 * Cached in the test class's ExtensionContext store, so it lives as long as the test class runs.
 */
public class TestClassInClassLoader {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestClassInClassLoader.class);

    private final ClassLoader classLoader;
    private final Class<?> testClass;
    private final Map<Object, Object> shadows = Collections.synchronizedMap(new IdentityHashMap<Object, Object>());
    private final Map<Object, Map<Field, Object>> snapshots = new IdentityHashMap<Object, Map<Field, Object>>();

    private TestClassInClassLoader(Class<?> originalTestClass) {
        List<Class<?>> chain = new ArrayList<Class<?>>();
        for (Class<?> c = originalTestClass; c != null; c = isInner(c) ? c.getEnclosingClass() : null) {
            chain.add(0, c);
        }
        Class<?> root = chain.get(0);
        String[] packagesToIgnore = new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(originalTestClass);
        if (chain.size() == 1) {
            this.classLoader = new MockClassLoaderFactory(originalTestClass, packagesToIgnore).createForClass(null);
        } else {
            Set<String> ignore = new LinkedHashSet<String>();
            Set<String> modify = new LinkedHashSet<String>();
            for (Class<?> c : chain) {
                ignore.addAll(Arrays.asList(new PowerMockIgnorePackagesExtractorImpl().getPackagesToIgnore(c)));
                modify.addAll(Arrays.asList(new PrepareForTestExtractorImpl().getTestClasses(c)));
                modify.addAll(Arrays.asList(new StaticConstructorSuppressExtractorImpl().getTestClasses(c)));
                modify.add(c.getName());
            }
            this.classLoader = MockClassLoaderBuilder.create(ByteCodeFramework.getByteCodeFrameworkForTestClass(root))
                .forTestClass(root)
                .addIgnorePackage(ignore.toArray(new String[0]))
                .addClassesToModify(modify.toArray(new String[0]))
                .addClassPathAdjuster(root.getAnnotation(org.powermock.core.classloader.annotations.UseClassPathAdjuster.class))
                .build();
        }
        for (Class<?> c : chain) {
            new MockPolicyInitializerImpl(c).initialize(classLoader);
        }
        try {
            this.testClass = Class.forName(originalTestClass.getName(), false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load " + originalTestClass + " in PowerMock's MockClassLoader", e);
        }
    }

    private static boolean isInner(Class<?> c) {
        return c.getEnclosingClass() != null && !Modifier.isStatic(c.getModifiers());
    }

    public static TestClassInClassLoader of(ExtensionContext context) {
        Class<?> originalTestClass = context.getRequiredTestClass();
        ExtensionContext classContext = context;
        while (!(classContext.getElement().isPresent() && classContext.getElement().get() == originalTestClass)
            && classContext.getParent().isPresent()) {
            classContext = classContext.getParent().get();
        }
        return classContext.getStore(NAMESPACE).getOrComputeIfAbsent(
            originalTestClass, TestClassInClassLoader::new, TestClassInClassLoader.class);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public Class<?> getTestClass() {
        return testClass;
    }

    public void createShadowInstance(Object originalInstance, ExtensionContext context) throws Exception {
        getShadowInstance(originalInstance, context);
    }

    public Object getShadowInstance(Object originalInstance, ExtensionContext context) {
        synchronized (shadows) {
            Object shadow = shadows.get(originalInstance);
            if (shadow == null) {
                try {
                    shadow = newShadow(originalInstance);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create MockClassLoader instance for " + originalInstance, e);
                }
            }
            return shadow;
        }
    }

    private Object newShadow(final Object original) throws Exception {
        Class<?> type = original.getClass();
        final Class<?> shadowType = Class.forName(type.getName(), false, classLoader);
        Object outer = null;
        Field outerField = outerField(type);
        if (outerField != null) {
            outerField.setAccessible(true);
            Object outerOriginal = outerField.get(original);
            outer = outerOriginal == null ? null : getShadowInstance(outerOriginal, null);
        }
        final Object outerShadow = outer;
        Object shadow = MockClassLoaderInvoker.withContextClassLoader(classLoader, () -> {
            try {
                if (outerShadow != null) {
                    Constructor<?> c = shadowType.getDeclaredConstructor(outerShadow.getClass());
                    c.setAccessible(true);
                    return c.newInstance(outerShadow);
                }
                Constructor<?> c = shadowType.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (NoSuchMethodException e) {
                // constructor with parameters resolved by Jupiter: the field values are copied from the original
                return Whitebox.newInstance(shadowType);
            }
        });
        shadows.put(original, shadow);
        // constructor-injected values, values set by extensions that ran before us
        syncToShadow(original, shadow, knownShadows());
        return shadow;
    }

    private static Field outerField(Class<?> type) {
        if (!isInner(type)) {
            return null;
        }
        for (Field f : type.getDeclaredFields()) {
            if (f.isSynthetic() && f.getType() == type.getEnclosingClass()) {
                return f;
            }
        }
        return null;
    }

    public Map<Object, Object> knownShadows() {
        synchronized (shadows) {
            return new IdentityHashMap<Object, Object>(shadows);
        }
    }

    /** Copies fields changed on the original instances (by Jupiter or other extensions) to their shadows. */
    public void syncToShadows() {
        Map<Object, Object> known = knownShadows();
        for (Map.Entry<Object, Object> e : known.entrySet()) {
            syncToShadow(e.getKey(), e.getValue(), known);
        }
    }

    private void syncToShadow(Object original, Object shadow, Map<Object, Object> known) {
        {
            Map<Field, Object> snapshot = snapshot(original);
            for (Field field : instanceFields(original.getClass())) {
                try {
                    Object value = field.get(original);
                    if (snapshot.containsKey(field) && snapshot.get(field) == value) {
                        continue;
                    }
                    snapshot.put(field, value);
                    if (field.isSynthetic()) {
                        continue;
                    }
                    Field target = shadow.getClass().getDeclaredField(field.getName());
                    target.setAccessible(true);
                    Object converted = CrossLoaderConverter.convert(value, classLoader, known);
                    if (field.getType().isPrimitive() || converted == null || target.getType().isInstance(converted)) {
                        if (!(converted == null && snapshotMissingOrNullOnShadow(target, shadow))) {
                            target.set(shadow, converted);
                        }
                    }
                } catch (Exception ignored) {
                    // not transferable: leave the shadow's own value
                }
            }
        }
    }

    private static boolean snapshotMissingOrNullOnShadow(Field target, Object shadow) throws IllegalAccessException {
        // never overwrite a value the shadow has (e.g. field initialiser, injected mock) with the original's null
        return target.get(shadow) != null || target.getType().isPrimitive();
    }

    /** Copies values the test code set on the shadows back to the originals, where the value fits both worlds. */
    public void syncFromShadows() {
        for (Map.Entry<Object, Object> e : knownShadows().entrySet()) {
            Object original = e.getKey();
            Object shadow = e.getValue();
            Map<Field, Object> snapshot = snapshot(original);
            for (Field field : instanceFields(original.getClass())) {
                if (field.isSynthetic()) {
                    continue;
                }
                try {
                    Field source = shadow.getClass().getDeclaredField(field.getName());
                    source.setAccessible(true);
                    Object value = source.get(shadow);
                    boolean fits = field.getType().isPrimitive()
                        ? source.getType() == field.getType()
                        : value == null || (field.getType().isInstance(value)
                            && CrossLoaderConverter.counterpart(value.getClass(), original.getClass().getClassLoader()) == value.getClass());
                    if (fits && field.get(original) != value) {
                        field.set(original, value);
                    }
                    snapshot.put(field, field.get(original));
                } catch (Exception ignored) {
                    // leave the original's value
                }
            }
        }
    }

    private Map<Field, Object> snapshot(Object original) {
        synchronized (snapshots) {
            Map<Field, Object> snapshot = snapshots.get(original);
            if (snapshot == null) {
                snapshot = new java.util.HashMap<Field, Object>();
                snapshots.put(original, snapshot);
            }
            return snapshot;
        }
    }

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        fields.add(f);
                    } catch (RuntimeException ignored) {
                        // inaccessible
                    }
                }
            }
        }
        return fields;
    }
}
