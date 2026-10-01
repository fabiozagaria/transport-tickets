package it.fabio.transport.application;

import it.fabio.transport.domain.*;
import it.fabio.transport.infrastructure.InMemoryTicketRepository;
import java.time.*;
import java.util.UUID;

/** Checks the application layer directly, without HTTP or external test libraries. */
public final class TicketServiceCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var directory = new DemoDirectory();
        var service = new TicketService(new InMemoryTicketRepository(), directory,
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
        var department = directory.authenticate("department-demo");
        var cut = directory.authenticate("cut-demo");
        var operator = directory.authenticate("operator-demo");
        var other = directory.authenticate("operator2-demo");
        var command = new CreateTicket("FAKE-001", new UUID(0, 1), new UUID(0, 2), TicketPriority.URGENT, null);
        try {
            service.create(cut, command);
            throw new AssertionError("CUT must not create tickets");
        } catch (ApplicationFailure e) {
            check(e.kind() == ApplicationFailure.Kind.FORBIDDEN, "Wrong access error");
        }
        var created = service.create(department, command);
        check(created.createdAt().equals(Instant.parse("2026-10-01T10:00:00Z")), "Server clock ignored");
        var assigned = service.act(cut, created.id(), TicketAction.ASSIGN, operator.id(), null);
        service.act(operator, created.id(), TicketAction.ACCEPT, null, null);
        check(assigned.status() == TicketStatus.ASSIGNED && assigned.history().size() == 2,
                "A previous snapshot must not change after an update");
        try {
            assigned.history().clear();
            throw new AssertionError("History must be immutable");
        } catch (UnsupportedOperationException expected) { }
        service.act(cut, created.id(), TicketAction.ASSIGN, other.id(), null);
        try {
            service.act(operator, created.id(), TicketAction.START, null, null);
            throw new AssertionError("Old operator must lose access after reassignment");
        } catch (ApplicationFailure e) {
            check(e.kind() == ApplicationFailure.Kind.NOT_FOUND, "Wrong visibility error");
        }
        var after = service.get(cut, created.id());
        check(after.status() == TicketStatus.ASSIGNED && after.history().size() == 4,
                "Failed operation changed the ticket");
        check(after.firstAssignedAt().equals(assigned.firstAssignedAt()), "Reassignment reset first assignment");
        check(service.list(operator).isEmpty(), "Old operator still sees ticket");
        System.out.println("Service checks passed: access, clock, immutable snapshots and reassignment.");
    }
}
