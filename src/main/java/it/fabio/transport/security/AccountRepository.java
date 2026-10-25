package it.fabio.transport.security;

import java.util.Optional;

public interface AccountRepository {
    Optional<UserAccount> findByUsername(String username);
}
