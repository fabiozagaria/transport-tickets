package it.fabio.transport.domain;

import java.util.Objects;
import java.util.UUID;

// Identità del dominio: le credenziali saranno gestite separatamente.
public record User(UUID id, String username, UserRole role, UUID departmentId) {
    public User(UUID id, String username, UserRole role) { this(id, username, role, null); }

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username obbligatorio");
    }
}
