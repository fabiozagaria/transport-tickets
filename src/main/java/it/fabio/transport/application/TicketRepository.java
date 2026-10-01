package it.fabio.transport.application;

import it.fabio.transport.domain.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Callbacks run atomically. They must return detached values, never mutable tickets. */
public interface TicketRepository {
    TicketView add(Ticket ticket);
    <T> Optional<T> withTicket(UUID id, Function<Ticket, T> operation);
    default <T> Optional<T> readTicket(UUID id, Function<Ticket, T> snapshot) {
        return withTicket(id, snapshot);
    }
    <T> List<T> readAll(Function<Ticket, T> snapshot);
}
