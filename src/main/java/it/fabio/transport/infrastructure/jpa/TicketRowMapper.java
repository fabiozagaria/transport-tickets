package it.fabio.transport.infrastructure.jpa;

import it.fabio.transport.domain.*;
import it.fabio.transport.application.RepositoryFailure;
import java.util.Objects;

final class TicketRowMapper {
    static void update(TicketRow row, Ticket t) {
        row.status=t.getStatus().name();
        row.assignedOperatorId=t.getAssignedOperator()==null?null:t.getAssignedOperator().id();
        row.assignedOperatorName=t.getAssignedOperator()==null?null:t.getAssignedOperator().username();
        row.firstAssignedAt=StoredInstant.from(t.getFirstAssignedAt()); row.completedAt=StoredInstant.from(t.getCompletedAt());
        for(int i=row.events.size();i<t.getHistory().size();i++) row.events.add(new TicketEventRow(row,i,t.getHistory().get(i)));
        row.eventCount=row.events.size();
    }
    static Ticket read(TicketRow r) {
        try {
            var t=new Ticket(r.id,r.patientCode,new Department(r.origin.getId(),r.originName),new Department(r.destination.getId(),r.destinationName),
                new User(r.creatorId,r.creatorName,UserRole.DEPARTMENT,r.creatorDepartmentId),TicketPriority.valueOf(r.priority),r.createdAt.value(),StoredInstant.value(r.scheduledAt));
            if(r.events.size()!=r.eventCount || r.eventCount<1) throw new IllegalStateException("Storico incompleto");
            for(int i=0;i<r.events.size();i++) {
                var stored=r.events.get(i); var e=stored.domain();
                if(stored.id.eventIndex!=i) throw new IllegalStateException("Sequenza eventi non valida");
                if(i>0) {
                    var actor=new User(e.actorId(),e.actorId().toString(),e.action().equals("ASSIGNED")?UserRole.CUT:UserRole.OPERATOR);
                    switch(e.action()) {
                        case "ASSIGNED" -> t.assign(actor,new User(e.assignedOperatorId(),e.assignedOperatorId().equals(r.assignedOperatorId)?r.assignedOperatorName:e.assignedOperatorId().toString(),UserRole.OPERATOR),e.occurredAt());
                        case "ACCEPTED" -> t.accept(actor,e.occurredAt());
                        case "STARTED" -> t.start(actor,e.occurredAt());
                        case "PATIENT_IDENTIFIED" -> t.identifyPatient(actor,r.patientCode,e.occurredAt());
                        case "IN_TRANSIT" -> t.depart(actor,e.occurredAt());
                        case "ARRIVED" -> t.arrive(actor,e.occurredAt());
                        case "COMPLETED" -> t.complete(actor,e.occurredAt());
                        default -> throw new IllegalStateException("Evento sconosciuto");
                    }
                }
                if(!t.getHistory().get(i).equals(e)) throw new IllegalStateException("Evento incoerente");
            }
            if(!t.getStatus().name().equals(r.status) || !Objects.equals(t.getFirstAssignedAt(),StoredInstant.value(r.firstAssignedAt))
                || !Objects.equals(t.getCompletedAt(),StoredInstant.value(r.completedAt))
                || !Objects.equals(t.getAssignedOperator()==null?null:t.getAssignedOperator().id(),r.assignedOperatorId)) throw new IllegalStateException("Snapshot incoerente");
            return t;
        } catch(RuntimeException e) { throw new RepositoryFailure(RepositoryFailure.Kind.CORRUPT_DATA,"Dati ticket non validi",e); }
    }
    static TicketRow create(Ticket t,DepartmentRow origin,DepartmentRow destination) {
        var r=new TicketRow();r.id=t.getId();r.patientCode=t.getPatientCode();r.origin=origin;r.destination=destination;
        r.originName=t.getOrigin().name();r.destinationName=t.getDestination().name();r.creatorId=t.getCreatedBy().id();
        r.creatorName=t.getCreatedBy().username();r.creatorDepartmentId=t.getCreatedBy().departmentId();r.priority=t.getPriority().name();
        r.createdAt=StoredInstant.from(t.getCreatedAt());r.scheduledAt=StoredInstant.from(t.getScheduledAt());update(r,t);return r;
    }
}
