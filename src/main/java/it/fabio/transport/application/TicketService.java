package it.fabio.transport.application;

import it.fabio.transport.domain.*;
import java.time.Clock;
import java.util.*;

/** Coordinates use cases and access checks independently of HTTP. */
public final class TicketService {
    private final TicketRepository repository;
    private final TransportDirectory directory;
    private final Clock clock;

    public TicketService(TicketRepository repository, TransportDirectory directory, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.directory = Objects.requireNonNull(directory);
        this.clock = Objects.requireNonNull(clock);
    }

    public TicketView create(User actor, CreateTicket command) {
        requireRole(actor, UserRole.DEPARTMENT);
        if (actor.departmentId() == null || !actor.departmentId().equals(command.originId()))
            throw new ApplicationFailure(ApplicationFailure.Kind.FORBIDDEN, "Reparto di origine non autorizzato");
        return repository.add(new Ticket(UUID.randomUUID(), command.patientCode(),
                directory.department(command.originId()), directory.department(command.destinationId()),
                actor, command.priority(), clock.instant(), command.scheduledAt()));
    }

    public List<TicketView> list(User actor) {
        return repository.readAll(t -> visible(actor, t) ? TicketView.from(t) : null);
    }

    public TicketView get(User actor, UUID id) {
        return repository.readTicket(id, t -> {
            requireVisible(actor, t);
            return TicketView.from(t);
        }).orElseThrow(TicketService::notFound);
    }

    public TicketView act(User actor, UUID id, TicketAction action, UUID operatorId, String patientCode) {
        return repository.withTicket(id, ticket -> {
            // Recheck under the same lock as mutation: assignment may have changed meanwhile.
            requireVisible(actor, ticket);
            requireRole(actor, action == TicketAction.ASSIGN ? UserRole.CUT : UserRole.OPERATOR);
            var now = clock.instant();
            switch (action) {
                case ASSIGN -> ticket.assign(actor, directory.operator(operatorId), now);
                case ACCEPT -> ticket.accept(actor, now);
                case START -> ticket.start(actor, now);
                case IDENTIFY -> ticket.identifyPatient(actor, patientCode, now);
                case DEPART -> ticket.depart(actor, now);
                case ARRIVE -> ticket.arrive(actor, now);
                case COMPLETE -> ticket.complete(actor, now);
            }
            return TicketView.from(ticket);
        }).orElseThrow(TicketService::notFound);
    }

    private static boolean visible(User actor, Ticket ticket) {
        return actor.role() == UserRole.CUT
                || (actor.role() == UserRole.DEPARTMENT && actor.departmentId() != null && actor.departmentId().equals(ticket.getOrigin().id()))
                || (actor.role() == UserRole.OPERATOR && ticket.getAssignedOperator() != null
                    && actor.id().equals(ticket.getAssignedOperator().id()));
    }

    private static void requireVisible(User actor, Ticket ticket) {
        if (!visible(actor, ticket)) throw notFound();
    }

    private static ApplicationFailure notFound() {
        return new ApplicationFailure(ApplicationFailure.Kind.NOT_FOUND, "Ticket inesistente");
    }

    private static void requireRole(User actor, UserRole role) {
        if (actor.role() != role)
            throw new ApplicationFailure(ApplicationFailure.Kind.FORBIDDEN, "Ruolo non autorizzato");
    }
}
