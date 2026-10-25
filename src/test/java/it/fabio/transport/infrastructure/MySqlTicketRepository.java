package it.fabio.transport.infrastructure;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.Ticket;
import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** MySQL is authoritative. Row locks serialize mutations across API instances. */
public final class MySqlTicketRepository implements TicketRepository {
    private final String url, username, password;
    private final RedisTicketCache cache;

    public MySqlTicketRepository(String url, String username, String password, RedisTicketCache cache) {
        this.url = url; this.username = username; this.password = password; this.cache = cache;
        try (var c = connect(); var statement = c.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS tickets (id CHAR(36) PRIMARY KEY, version BIGINT NOT NULL, payload LONGBLOB NOT NULL) ENGINE=InnoDB");
        } catch (SQLException e) { throw unavailable(e); }
    }

    private Connection connect() throws SQLException { return DriverManager.getConnection(url, username, password); }

    @Override public TicketView add(Ticket ticket) {
        byte[] bytes = TicketCodec.encode(ticket);
        try (var c = connect(); var s = c.prepareStatement("INSERT INTO tickets(id, version, payload) VALUES (?, 1, ?)")) {
            s.setString(1, ticket.getId().toString()); s.setBytes(2, bytes); s.executeUpdate();
            return TicketView.from(ticket);
        } catch (SQLException e) { throw unavailable(e); }
    }

    @Override public <T> Optional<T> withTicket(UUID id, Function<Ticket, T> operation) {
        return transaction(c -> {
            try (var s = c.prepareStatement("SELECT payload FROM tickets WHERE id = ? FOR UPDATE")) {
                s.setString(1, id.toString());
                try (var rows = s.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    byte[] before = rows.getBytes(1);
                    Ticket ticket = TicketCodec.decode(before);
                    T result = operation.apply(ticket);
                    byte[] after = TicketCodec.encode(ticket);
                    if (!Arrays.equals(before, after)) {
                        try (var update = c.prepareStatement("UPDATE tickets SET payload = ?, version = version + 1 WHERE id = ?")) {
                            update.setBytes(1, after); update.setString(2, id.toString()); update.executeUpdate();
                        }
                    }
                    return Optional.of(result);
                }
            }
        });
    }

    @Override public <T> Optional<T> readTicket(UUID id, Function<Ticket, T> snapshot) {
        return transaction(c -> {
            // Version and payload stay consistent while the share lock is held.
            try (var s = c.prepareStatement("SELECT version FROM tickets WHERE id = ? FOR SHARE")) {
                s.setString(1, id.toString());
                try (var rows = s.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    String key = "tickets:v3:" + id + ":" + rows.getLong(1);
                    byte[] bytes = cache == null ? null : cache.get(key);
                    Ticket ticket = null;
                    if (bytes != null) {
                        try { ticket = TicketCodec.decode(bytes); if (!ticket.getId().equals(id)) ticket = null; }
                        catch (RuntimeException ignored) { /* repair malformed cache from MySQL */ }
                    }
                    if (ticket == null) {
                        try (var payload = c.prepareStatement("SELECT payload FROM tickets WHERE id = ?")) {
                            payload.setString(1, id.toString());
                            try (var data = payload.executeQuery()) { data.next(); bytes = data.getBytes(1); }
                        }
                        ticket = TicketCodec.decode(bytes);
                        if (cache != null) cache.put(key, bytes);
                    }
                    return Optional.of(snapshot.apply(ticket));
                }
            }
        });
    }

    @Override public <T> List<T> readAll(Function<Ticket, T> snapshot) {
        try (var c = connect(); var s = c.prepareStatement("SELECT payload FROM tickets ORDER BY id"); var rows = s.executeQuery()) {
            var result = new ArrayList<T>();
            while (rows.next()) {
                T value = snapshot.apply(TicketCodec.decode(rows.getBytes(1)));
                if (value != null) result.add(value);
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw unavailable(e); }
    }

    private <T> T transaction(SqlOperation<T> operation) {
        try (var c = connect()) {
            c.setAutoCommit(false);
            try {
                T result = operation.run(c); c.commit(); return result;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw unavailable(e); }
    }

    private static RepositoryFailure unavailable(SQLException e) { return new RepositoryFailure(RepositoryFailure.Kind.UNAVAILABLE, "Archivio MySQL non disponibile", e); }
    @FunctionalInterface private interface SqlOperation<T> { T run(Connection c) throws SQLException; }
}
