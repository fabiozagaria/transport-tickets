package it.fabio.transport.application;

import it.fabio.transport.domain.*;
import java.util.List;
import java.util.UUID;

/** User/department catalogue independent of authentication and HTTP. */
public interface TransportDirectory {
    List<User> users();
    List<Department> departments();
    Department department(UUID id);
    User operator(UUID id);
}
