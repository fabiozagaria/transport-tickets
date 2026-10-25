package it.fabio.transport.infrastructure.jpa;

import it.fabio.transport.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity @Table(name="ticket_events")
public class TicketEventRow {
    @EmbeddedId TicketEventKey id;
    @MapsId("ticketId") @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ticket_id") TicketRow ticket;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="epochSecond",column=@Column(name="occurred_second",nullable=false)),
        @AttributeOverride(name="nano",column=@Column(name="occurred_nano",nullable=false))}) StoredInstant occurredAt;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="actor_id",nullable=false,length=36) UUID actorId;
    @Column(nullable=false,length=40) String action;
    @Column(name="resulting_status",nullable=false,length=30) String resultingStatus;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="assigned_operator_id",length=36) UUID assignedOperatorId;
    protected TicketEventRow() {}
    TicketEventRow(TicketRow parent,int index,TicketEvent event) {
        id=new TicketEventKey(parent.id,index); ticket=parent;occurredAt=StoredInstant.from(event.occurredAt());
        actorId=event.actorId();action=event.action();resultingStatus=event.resultingStatus().name();assignedOperatorId=event.assignedOperatorId();
    }
    TicketEvent domain() { return new TicketEvent(occurredAt.value(),actorId,action,TicketStatus.valueOf(resultingStatus),assignedOperatorId); }
}
