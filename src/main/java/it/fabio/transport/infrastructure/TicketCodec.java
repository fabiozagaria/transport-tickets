package it.fabio.transport.infrastructure;

import it.fabio.transport.domain.*;
import it.fabio.transport.application.RepositoryFailure;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** Versioned storage format. Replays domain events rather than bypassing its rules. */
public final class TicketCodec {
    public static byte[] encode(Ticket t) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(3);
            out.writeUTF(t.getId().toString()); out.writeUTF(t.getPatientCode());
            out.writeUTF(t.getOrigin().id().toString()); out.writeUTF(t.getOrigin().name());
            out.writeUTF(t.getDestination().id().toString()); out.writeUTF(t.getDestination().name());
            out.writeUTF(t.getCreatedBy().id().toString()); out.writeUTF(t.getCreatedBy().username());
            out.writeUTF(t.getCreatedBy().departmentId() == null ? "" : t.getCreatedBy().departmentId().toString());
            out.writeUTF(t.getPriority().name()); out.writeUTF(t.getCreatedAt().toString());
            out.writeUTF(t.getScheduledAt() == null ? "" : t.getScheduledAt().toString());
            out.writeUTF(t.getAssignedOperator() == null ? "" : t.getAssignedOperator().id().toString());
            out.writeUTF(t.getAssignedOperator() == null ? "" : t.getAssignedOperator().username());
            out.writeInt(t.getHistory().size());
            for (var e : t.getHistory()) {
                out.writeUTF(e.occurredAt().toString()); out.writeUTF(e.actorId().toString());
                out.writeUTF(e.action()); out.writeUTF(e.resultingStatus().name());
                out.writeUTF(e.assignedOperatorId() == null ? "" : e.assignedOperatorId().toString());
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new RepositoryFailure(RepositoryFailure.Kind.SERIALIZATION, "Ticket non serializzabile", e); }
    }

    public static Ticket decode(byte[] bytes) {
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            int format = in.readInt();
            if (format != 1 && format != 2 && format != 3) throw new IOException("Versione formato sconosciuta");
            UUID id = UUID.fromString(in.readUTF()); String patient = in.readUTF();
            var origin = new Department(UUID.fromString(in.readUTF()), in.readUTF());
            var destination = new Department(UUID.fromString(in.readUTF()), in.readUTF());
            UUID creatorId = UUID.fromString(in.readUTF()); String creatorName = in.readUTF();
            String creatorDepartment = format >= 3 ? in.readUTF() : origin.id().toString();
            var creator = new User(creatorId, creatorName, UserRole.DEPARTMENT,
                    creatorDepartment.isEmpty() ? null : UUID.fromString(creatorDepartment));
            var priority = TicketPriority.valueOf(in.readUTF()); var created = Instant.parse(in.readUTF());
            String scheduled = in.readUTF();
            var ticket = new Ticket(id, patient, origin, destination, creator, priority, created,
                    scheduled.isEmpty() ? null : Instant.parse(scheduled));
            String currentOperatorId = format >= 2 ? in.readUTF() : "";
            String currentOperatorName = format >= 2 ? in.readUTF() : "";
            if (currentOperatorId.isEmpty() != currentOperatorName.isEmpty()) throw new IOException("Operatore non valido");
            int count = in.readInt();
            if (count < 1 || count > bytes.length) throw new IOException("Storico non valido");
            for (int i = 0; i < count; i++) {
                Instant now = Instant.parse(in.readUTF()); UUID actorId = UUID.fromString(in.readUTF());
                String action = in.readUTF(); var state = TicketStatus.valueOf(in.readUTF());
                String assigned = in.readUTF();
                var expected = new TicketEvent(now, actorId, action, state,
                        assigned.isEmpty() ? null : UUID.fromString(assigned));
                if (i > 0) {
                    var actor = new User(actorId, actorId.toString(),
                            action.equals("ASSIGNED") ? UserRole.CUT : UserRole.OPERATOR);
                    switch (action) {
                        case "ASSIGNED" -> ticket.assign(actor,
                                new User(expected.assignedOperatorId(), assigned.equals(currentOperatorId) ? currentOperatorName : assigned, UserRole.OPERATOR), now);
                        case "ACCEPTED" -> ticket.accept(actor, now);
                        case "STARTED" -> ticket.start(actor, now);
                        case "PATIENT_IDENTIFIED" -> ticket.identifyPatient(actor, patient, now);
                        case "IN_TRANSIT" -> ticket.depart(actor, now);
                        case "ARRIVED" -> ticket.arrive(actor, now);
                        case "COMPLETED" -> ticket.complete(actor, now);
                        default -> throw new IOException("Evento sconosciuto");
                    }
                }
                if (!ticket.getHistory().get(i).equals(expected)) throw new IOException("Evento incoerente");
            }
            if (format >= 2 && !currentOperatorId.equals(ticket.getAssignedOperator() == null ? "" : ticket.getAssignedOperator().id().toString()))
                throw new IOException("Operatore corrente incoerente");
            if (in.available() != 0) throw new IOException("Dati inattesi");
            return ticket;
        } catch (IOException | RuntimeException e) { throw new RepositoryFailure(RepositoryFailure.Kind.CORRUPT_DATA, "Dati ticket non validi", e); }
    }
}
