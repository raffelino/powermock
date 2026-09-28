package powermock.modules.junit5.gaps.support;

public class UserRepository {
    public String find(int id) {
        return "real-user-" + id;
    }
}
