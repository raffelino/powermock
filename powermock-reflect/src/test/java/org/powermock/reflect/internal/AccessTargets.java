package org.powermock.reflect.internal;

/**
 * Targets for {@link WhiteboxImplAccessTest}. Kept in their own top-level class so they are not nestmates of the
 * test: reflective access to their private members only works if WhiteboxImpl made them accessible.
 */
class AccessTargets {

    static class TwoFields {
        private int first = 1;
        private String second = "s";
    }

    static class TwoMethods {
        private String priv(StringBuilder sb) {
            return "priv";
        }

        private String other(Integer i) {
            return "other";
        }
    }

    interface Iface {
        void onlyOne(Character c);
    }

    static class Prims {
        private static final int STATIC_INT = Integer.parseInt("1");
        private static final String STATIC_STRING = String.valueOf("a");

        private final int i;
        private final short s;
        private final long l;
        private final byte b;
        private final boolean z;
        private final float f;
        private final double d;
        private final char c;
        private final String o;

        Prims(int seed) {
            i = seed;
            s = (short) seed;
            l = seed;
            b = (byte) seed;
            z = seed == 0;
            f = seed;
            d = seed;
            c = (char) ('a' + seed);
            o = String.valueOf(seed);
        }
    }
}
