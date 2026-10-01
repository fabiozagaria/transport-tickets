package db.migration;

import it.fabio.transport.domain.*;
import it.fabio.transport.infrastructure.TicketCodec;
import org.flywaydb.core.api.migration.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Decode and validate every historical event before importing; retain the source archive. */
public class V3__ImportLegacyTickets extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();
        // MySQL DDL is not transactional. This data-only migration owns a transaction explicitly.
        boolean previous=c.getAutoCommit();c.setAutoCommit(false);
        try {
            var payloads=new ArrayList<byte[]>();var versions=new ArrayList<Long>();var ids=new ArrayList<String>();
            try(var q=c.createStatement();var rows=q.executeQuery("SELECT id,version,payload FROM tickets ORDER BY id")) {
                while(rows.next()) {ids.add(rows.getString(1));versions.add(rows.getLong(2));payloads.add(rows.getBytes(3));}
            }
            for(int i=0;i<payloads.size();i++) {
                Ticket t=TicketCodec.decode(payloads.get(i));
                if(!t.getId().toString().equals(ids.get(i))) throw new SQLException("Legacy ticket ID mismatch");
                insert(c,t,versions.get(i));
            }
            c.commit();
        } catch(Exception e) {c.rollback();throw e;} finally {c.setAutoCommit(previous);}
    }
    private static void insert(Connection c,Ticket t,long legacyVersion) throws SQLException {
        for(var d:List.of(t.getOrigin(),t.getDestination())) {
            try(var q=c.prepareStatement("INSERT IGNORE INTO departments(id,name) VALUES (?,?)")) {q.setString(1,d.id().toString());q.setString(2,d.name());q.executeUpdate();}
        }
        String sql="INSERT INTO ticket_records(id,revision,legacy_version,patient_code,origin_id,origin_name,destination_id,destination_name,creator_id,creator_name,creator_department_id,priority,status,assigned_operator_id,assigned_operator_name,created_second,created_nano,scheduled_second,scheduled_nano,first_assigned_second,first_assigned_nano,completed_second,completed_nano,event_count) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(var q=c.prepareStatement(sql)) {
            Object[] values={t.getId(),0L,legacyVersion,t.getPatientCode(),t.getOrigin().id(),t.getOrigin().name(),t.getDestination().id(),t.getDestination().name(),t.getCreatedBy().id(),t.getCreatedBy().username(),t.getCreatedBy().departmentId(),t.getPriority().name(),t.getStatus().name(),t.getAssignedOperator()==null?null:t.getAssignedOperator().id(),t.getAssignedOperator()==null?null:t.getAssignedOperator().username(),seconds(t.getCreatedAt()),nanos(t.getCreatedAt()),seconds(t.getScheduledAt()),nanos(t.getScheduledAt()),seconds(t.getFirstAssignedAt()),nanos(t.getFirstAssignedAt()),seconds(t.getCompletedAt()),nanos(t.getCompletedAt()),t.getHistory().size()};
            bind(q,values);q.executeUpdate();
        }
        for(int i=0;i<t.getHistory().size();i++) {
            var e=t.getHistory().get(i);
            try(var q=c.prepareStatement("INSERT INTO ticket_events(ticket_id,event_index,occurred_second,occurred_nano,actor_id,action,resulting_status,assigned_operator_id) VALUES (?,?,?,?,?,?,?,?)")) {
                bind(q,new Object[]{t.getId(),i,seconds(e.occurredAt()),nanos(e.occurredAt()),e.actorId(),e.action(),e.resultingStatus().name(),e.assignedOperatorId()});q.executeUpdate();
            }
        }
    }
    private static void bind(PreparedStatement q,Object[] values) throws SQLException {for(int i=0;i<values.length;i++) q.setObject(i+1,values[i] instanceof UUID?values[i].toString():values[i]);}
    private static Long seconds(Instant time) {return time==null?null:time.getEpochSecond();}
    private static Integer nanos(Instant time) {return time==null?null:time.getNano();}
}
