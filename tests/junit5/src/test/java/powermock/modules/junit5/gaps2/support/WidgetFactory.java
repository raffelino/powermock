package powermock.modules.junit5.gaps2.support;

/** Calls {@code new Widget(..)}: constructor interception needs this class prepared. */
public class WidgetFactory {
    public Widget create(String name) {
        return new Widget(name);
    }
}
