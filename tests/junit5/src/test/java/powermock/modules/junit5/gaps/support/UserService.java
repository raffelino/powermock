package powermock.modules.junit5.gaps.support;

/** Object under test for @InjectMocks: two collaborators, one static call, one constructor call. */
public class UserService {
    private UserRepository repository;
    private Greeter greeter;

    public UserRepository getRepository() {
        return repository;
    }

    public Greeter getGreeter() {
        return greeter;
    }

    public String describe(int id) {
        return greeter.greet(repository.find(id));
    }

    public String describeWithId(int id) {
        return repository.find(id) + "#" + IdGenerator.next();
    }

    public String audit(String event) {
        return new AuditLog().record(event);
    }
}
