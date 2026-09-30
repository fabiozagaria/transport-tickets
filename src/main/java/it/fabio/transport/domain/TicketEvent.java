package it.fabio.transport.domain;

import java.time.Instant;
import java.util.UUID;

public record TicketEvent(Instant occurredAt, UUID actorId, String action,
                          TicketStatus resultingStatus, UUID assignedOperatorId) {}
