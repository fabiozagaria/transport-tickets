package it.fabio.transport.security;

import it.fabio.transport.domain.User;

public record UserAccount(User user, String passwordHash, boolean enabled) {}
