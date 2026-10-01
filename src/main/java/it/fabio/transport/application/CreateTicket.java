package it.fabio.transport.application;

import it.fabio.transport.domain.TicketPriority;
import java.time.Instant;
import java.util.UUID;

public record CreateTicket(String patientCode, UUID originId, UUID destinationId,
                           TicketPriority priority, Instant scheduledAt) {}
