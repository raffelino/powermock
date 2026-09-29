package powermock.modules.junit5.gaps2.support;

/** Prepared class that throws the user exceptions. */
public class Validator {
    public static final String EXPECTED_PREFIX = "expected: ";

    public static void check(String input) {
        if (input.isEmpty()) {
            throw new UserFailure(EXPECTED_PREFIX + "empty input");
        }
    }

    public static int parse(String input) throws CheckedUserFailure {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new CheckedUserFailure("not a number: " + input);
        }
    }
}
