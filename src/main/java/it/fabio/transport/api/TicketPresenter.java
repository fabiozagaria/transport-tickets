package it.fabio.transport.api;

import it.fabio.transport.application.TicketView;
import java.util.*;

/** Converts immutable application results to the HTTP response representation. */
final class TicketPresenter {
    static Map<String, Object> snapshot(TicketView t) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", t.id()); result.put("patientCode", t.patientCode());
        result.put("originId", t.origin().id()); result.put("destinationId", t.destination().id());
        result.put("createdById", t.createdBy().id()); result.put("priority", t.priority());
        result.put("status", t.status());
        result.put("assignedOperatorId", t.assignedOperator() == null ? null : t.assignedOperator().id());
        result.put("createdAt", t.createdAt()); result.put("scheduledAt", t.scheduledAt());
        result.put("firstAssignedAt", t.firstAssignedAt()); result.put("completedAt", t.completedAt());
        result.put("assignmentDeadline", t.assignmentDeadline()); result.put("completionDeadline", t.completionDeadline());
        result.put("history", t.history().stream().map(e -> {
            var event = new LinkedHashMap<String, Object>();
            event.put("occurredAt", e.occurredAt()); event.put("actorId", e.actorId());
            event.put("action", e.action()); event.put("resultingStatus", e.resultingStatus());
            event.put("assignedOperatorId", e.assignedOperatorId()); return event;
        }).toList());
        return result;
    }

}
