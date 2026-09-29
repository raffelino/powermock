package powermock.modules.junit5.gaps2.support;

public class Widget {
    private final String name;

    public Widget(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
