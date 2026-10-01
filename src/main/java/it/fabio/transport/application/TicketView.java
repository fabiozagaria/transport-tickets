package it.fabio.transport.application;

import it.fabio.transport.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable snapshot: no live mutable Ticket leaves the repository operation. */
public record TicketView(UUID id, String patientCode, Department origin, Department destination,
                         User createdBy, TicketPriority priority, TicketStatus status, User assignedOperator,
                         Instant createdAt, Instant scheduledAt, Instant firstAssignedAt, Instant completedAt,
                         Instant assignmentDeadline, Instant completionDeadline, List<TicketEvent> history) {
    public TicketView { history = List.copyOf(history); }

    public static TicketView from(Ticket t) {
        return new TicketView(t.getId(), t.getPatientCode(), t.getOrigin(), t.getDestination(),
                t.getCreatedBy(), t.getPriority(), t.getStatus(), t.getAssignedOperator(),
                t.getCreatedAt(), t.getScheduledAt(), t.getFirstAssignedAt(), t.getCompletedAt(),
                t.assignmentDeadline(), t.completionDeadline(), t.getHistory());
    }
}
