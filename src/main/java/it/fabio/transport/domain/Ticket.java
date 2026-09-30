package it.fabio.transport.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Ticket {
    private final UUID id;
    private final String patientCode;
    private final Department origin;
    private final Department destination;
    private final User createdBy;
    private final TicketPriority priority;
    private final Instant createdAt;
    private final Instant scheduledAt;
    private final List<TicketEvent> history = new ArrayList<>();
    private TicketStatus status = TicketStatus.UNASSIGNED;
    private User assignedOperator;
    private Instant firstAssignedAt;
    private Instant completedAt;

    public Ticket(UUID id, String patientCode, Department origin, Department destination,
                  User createdBy, TicketPriority priority, Instant createdAt, Instant scheduledAt) {
        this.id = Objects.requireNonNull(id, "id");
        if (patientCode == null || patientCode.isBlank()) throw new IllegalArgumentException("Codice paziente obbligatorio");
        this.patientCode = patientCode;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        requireRole(createdBy, UserRole.DEPARTMENT);
        this.priority = Objects.requireNonNull(priority, "priority");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (scheduledAt != null && scheduledAt.isBefore(createdAt)) throw new IllegalArgumentException("Programmazione precedente alla creazione");
        this.scheduledAt = scheduledAt;
        record(createdBy, "CREATED", createdAt);
    }

    public void assign(User cut, User operator, Instant now) {
        requireRole(cut, UserRole.CUT);
        requireRole(operator, UserRole.OPERATOR);
        if (status != TicketStatus.UNASSIGNED && status != TicketStatus.ASSIGNED && status != TicketStatus.ACCEPTED)
            throw new IllegalStateException("Ticket già iniziato: riassegnazione vietata");
        validateTime(now);
        assignedOperator = operator;
        if (firstAssignedAt == null) firstAssignedAt = now;
        status = TicketStatus.ASSIGNED;
        record(cut, "ASSIGNED", now);
    }

    public void accept(User operator, Instant now) {
        advance(operator, TicketStatus.ASSIGNED, TicketStatus.ACCEPTED, now);
    }
    public void start(User operator, Instant now) {
        advance(operator, TicketStatus.ACCEPTED, TicketStatus.STARTED, now);
    }
    public void identifyPatient(User operator, String scannedCode, Instant now) {
        requireAssignedOperator(operator);
        requireStatus(TicketStatus.STARTED);
        if (!patientCode.equals(scannedCode)) throw new IllegalArgumentException("Codice non corrispondente al paziente previsto");
        advance(operator, TicketStatus.STARTED, TicketStatus.PATIENT_IDENTIFIED, now);
    }
    public void depart(User operator, Instant now) {
        advance(operator, TicketStatus.PATIENT_IDENTIFIED, TicketStatus.IN_TRANSIT, now);
    }
    public void arrive(User operator, Instant now) {
        advance(operator, TicketStatus.IN_TRANSIT, TicketStatus.ARRIVED, now);
    }
    public void complete(User operator, Instant now) {
        advance(operator, TicketStatus.ARRIVED, TicketStatus.COMPLETED, now);
        completedAt = now;
    }

    // Ipotesi del prototipo: 20 minuti per ciascuna fase; da verificare.
    public Instant assignmentDeadline() {
        return priority == TicketPriority.URGENT ? createdAt.plus(Duration.ofMinutes(20)) : null;
    }
    public Instant completionDeadline() {
        return priority == TicketPriority.URGENT && firstAssignedAt != null
                ? firstAssignedAt.plus(Duration.ofMinutes(20)) : null;
    }

    private void advance(User actor, TicketStatus expected, TicketStatus next, Instant now) {
        requireAssignedOperator(actor);
        requireStatus(expected);
        validateTime(now);
        status = next;
        record(actor, next.name(), now);
    }
    private void requireAssignedOperator(User actor) {
        requireRole(actor, UserRole.OPERATOR);
        if (assignedOperator == null || !assignedOperator.id().equals(actor.id()))
            throw new IllegalArgumentException("Ticket assegnato a un altro operatore");
    }
    private static void requireRole(User actor, UserRole role) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != role) throw new IllegalArgumentException("Ruolo non autorizzato");
    }
    private void requireStatus(TicketStatus expected) {
        if (status != expected) throw new IllegalStateException("Stato richiesto: " + expected + "; attuale: " + status);
    }
    private void validateTime(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(history.get(history.size() - 1).occurredAt()))
            throw new IllegalArgumentException("Evento precedente all'ultima modifica");
    }
    private void record(User actor, String action, Instant now) {
        history.add(new TicketEvent(now, actor.id(), action, status,
                assignedOperator == null ? null : assignedOperator.id()));
    }

    public UUID getId() { return id; }
    public String getPatientCode() { return patientCode; }
    public Department getOrigin() { return origin; }
    public Department getDestination() { return destination; }
    public User getCreatedBy() { return createdBy; }
    public TicketPriority getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public User getAssignedOperator() { return assignedOperator; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getFirstAssignedAt() { return firstAssignedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<TicketEvent> getHistory() { return List.copyOf(history); }
}
