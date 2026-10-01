package it.fabio.transport.infrastructure;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.Ticket;
import java.util.*;
import java.util.function.Function;

/** One lock per repository instance; it only protects data operations and snapshots. */
public final class InMemoryTicketRepository implements TicketRepository {
    private final Map<UUID, Ticket> tickets = new LinkedHashMap<>();

    @Override
    public synchronized TicketView add(Ticket ticket) {
        if (tickets.containsKey(ticket.getId())) throw new IllegalArgumentException("ID ticket già presente");
        tickets.put(ticket.getId(), ticket);
        return TicketView.from(ticket);
    }

    @Override
    public synchronized <T> Optional<T> withTicket(UUID id, Function<Ticket, T> operation) {
        Ticket ticket = tickets.get(id);
        return ticket == null ? Optional.empty() : Optional.of(operation.apply(ticket));
    }

    @Override
    public synchronized <T> List<T> readAll(Function<Ticket, T> snapshot) {
        return tickets.values().stream().map(snapshot).filter(Objects::nonNull).toList();
    }
}
