package powermock.modules.junit5.gaps2.support;

/** Enum converted by Jupiter from CSV columns. */
public enum Tier {
    BRONZE, GOLD;

    public String tagged() {
        return name().toLowerCase() + "-" + Ids.next();
    }
}
