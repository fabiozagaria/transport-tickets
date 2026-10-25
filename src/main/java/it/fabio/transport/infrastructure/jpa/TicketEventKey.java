package it.fabio.transport.infrastructure.jpa;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.util.*;

@Embeddable
public class TicketEventKey implements Serializable {
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="ticket_id",length=36) UUID ticketId;
    @Column(name="event_index") int eventIndex;
    protected TicketEventKey() {}
    TicketEventKey(UUID ticketId,int eventIndex) { this.ticketId=ticketId;this.eventIndex=eventIndex; }
    @Override public boolean equals(Object value) { return value instanceof TicketEventKey other && Objects.equals(ticketId,other.ticketId) && eventIndex==other.eventIndex; }
    @Override public int hashCode() { return Objects.hash(ticketId,eventIndex); }
}
