package it.fabio.transport.application;

import it.fabio.transport.domain.*;
import java.util.*;

/** Fixed demo identities and catalogue. Not a production authentication system. */
public final class DemoDirectory implements TransportDirectory {
    private final Map<String, User> identities;
    private final Map<UUID, User> users;
    private final Map<UUID, Department> departments;

    public DemoDirectory() {
        var identities = new LinkedHashMap<String, User>();
        identities.put("department-demo", new User(new UUID(0, 1), "department-demo", UserRole.DEPARTMENT, new UUID(0, 1)));
        identities.put("cut-demo", new User(new UUID(0, 2), "cut-demo", UserRole.CUT));
        identities.put("operator-demo", new User(new UUID(0, 3), "operator-demo", UserRole.OPERATOR));
        identities.put("operator2-demo", new User(new UUID(0, 4), "operator2-demo", UserRole.OPERATOR));
        this.identities = Map.copyOf(identities);
        var users = new HashMap<UUID, User>();
        identities.values().forEach(u -> users.put(u.id(), u));
        this.users = Map.copyOf(users);
        this.departments = Map.of(
                new UUID(0, 1), new Department(new UUID(0, 1), "Reparto demo"),
                new UUID(0, 2), new Department(new UUID(0, 2), "Radiologia demo"));
    }

    public User authenticate(String token) { return identities.get(token); }
    public List<User> users() { return users.values().stream().sorted(Comparator.comparing(User::username)).toList(); }
    public List<Department> departments() { return departments.values().stream().sorted(Comparator.comparing(Department::id)).toList(); }

    public Department department(UUID id) {
        var department = departments.get(id);
        if (department == null) throw new IllegalArgumentException("Reparto inesistente");
        return department;
    }

    public User operator(UUID id) {
        var operator = users.get(id);
        if (operator == null || operator.role() != UserRole.OPERATOR)
            throw new IllegalArgumentException("Operatore inesistente");
        return operator;
    }
}
