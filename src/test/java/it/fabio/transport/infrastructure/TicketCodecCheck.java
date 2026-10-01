package it.fabio.transport.infrastructure;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.*;
import java.io.*;
import java.time.Instant;
import java.util.*;

public final class TicketCodecCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void roundTrip(Ticket t) {
        check(TicketView.from(t).equals(TicketView.from(TicketCodec.decode(TicketCodec.encode(t)))), "Snapshot changed after serialization");
    }
    public static void main(String[] args) throws Exception {
        var d = new DemoDirectory();
        var department = d.authenticate("department-demo"); var cut = d.authenticate("cut-demo");
        var operator = d.authenticate("operator-demo"); var other = d.authenticate("operator2-demo");
        Instant now = Instant.parse("2026-10-01T10:00:00.123456789Z");
        var t = new Ticket(UUID.randomUUID(), "FAKE-😀", d.department(new UUID(0, 1)), d.department(new UUID(0, 2)),
                department, TicketPriority.URGENT, now, now.plusSeconds(3600));
        roundTrip(t);
        t.assign(cut, operator, now); roundTrip(t);
        t.accept(operator, now); roundTrip(t);
        t.assign(cut, other, now); roundTrip(t);
        t.accept(other, now); t.start(other, now); roundTrip(t);
        t.identifyPatient(other, t.getPatientCode(), now); t.depart(other, now); t.arrive(other, now); t.complete(other, now);
        roundTrip(t);
        // Frozen v1 layout fixture: the old format never contained operator usernames.
        for (int format : new int[] {1,2}) {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeInt(format); out.writeUTF(t.getId().toString()); out.writeUTF(t.getPatientCode());
        out.writeUTF(t.getOrigin().id().toString()); out.writeUTF(t.getOrigin().name());
        out.writeUTF(t.getDestination().id().toString()); out.writeUTF(t.getDestination().name());
        out.writeUTF(department.id().toString()); out.writeUTF(department.username());
        out.writeUTF(t.getPriority().name()); out.writeUTF(now.toString()); out.writeUTF(t.getScheduledAt().toString());
        if (format == 2) { out.writeUTF(other.id().toString()); out.writeUTF(other.username()); }
        out.writeInt(t.getHistory().size());
        for (var e : t.getHistory()) {
            out.writeUTF(e.occurredAt().toString()); out.writeUTF(e.actorId().toString()); out.writeUTF(e.action());
            out.writeUTF(e.resultingStatus().name()); out.writeUTF(e.assignedOperatorId() == null ? "" : e.assignedOperatorId().toString());
        }
        var legacy = TicketCodec.decode(bytes.toByteArray());
        check(legacy.getHistory().equals(t.getHistory()) && legacy.getStatus() == TicketStatus.COMPLETED, "Legacy history unreadable");
        check(legacy.getAssignedOperator().username().equals(format == 1 ? other.id().toString() : other.username()), "Legacy fallback changed");
        check(legacy.getCreatedBy().departmentId().equals(t.getOrigin().id()), "Legacy creator department missing");
        roundTrip(legacy);
        }
        for (byte[] bad : new byte[][] {new byte[] {1}, new byte[] {0, 0, 0, 99}, Arrays.copyOf(TicketCodec.encode(t), 30)}) {
            try { TicketCodec.decode(bad); throw new AssertionError("Corrupt payload accepted"); }
            catch (RepositoryFailure e) { check(e.kind() == RepositoryFailure.Kind.CORRUPT_DATA, "Wrong corruption classification"); }
        }
        System.out.println("Codec checks passed: operator name, snapshots, deadlines, history, legacy v1/v2, corrupt data.");
    }
}
