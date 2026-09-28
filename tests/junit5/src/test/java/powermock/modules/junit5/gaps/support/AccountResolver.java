package powermock.modules.junit5.gaps.support;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Custom resolver that supplies an {@link Account} owned by {@link #OWNER}.
 * <p>
 * It is deliberately class-loader neutral: it matches the parameter type by name and instantiates
 * the parameter's own declared type. So it works whether the executable Jupiter hands it belongs to
 * the application class loader or to PowerMock's MockClassLoader; an implementation is free to
 * resolve parameters against either (or to transfer the value into the MockClassLoader).
 */
public class AccountResolver implements ParameterResolver {

    public static final String OWNER = "alice";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().getName().equals(Account.class.getName());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        try {
            return parameterContext.getParameter().getType().getConstructor(String.class).newInstance(OWNER);
        } catch (ReflectiveOperationException e) {
            throw new ParameterResolutionException("Cannot create Account", e);
        }
    }
}
