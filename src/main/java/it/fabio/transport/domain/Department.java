package it.fabio.transport.domain;

import java.util.Objects;
import java.util.UUID;

public record Department(UUID id, String name) {
    public Department {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome reparto obbligatorio");
    }
}
