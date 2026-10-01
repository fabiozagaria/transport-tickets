package it.fabio.transport.infrastructure.jpa;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.*;

/** Persistence model, never exposed through HTTP or used to bypass domain transitions. */
@Entity @Table(name="ticket_records")
public class TicketRow {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) UUID id;
    @Version @Column(nullable=false) Long revision;
    @Column(name="legacy_version") Long legacyVersion;
    @Column(name="patient_code",nullable=false,columnDefinition="text") String patientCode;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="origin_id",nullable=false) DepartmentRow origin;
    @Column(name="origin_name",nullable=false,length=255) String originName;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="destination_id",nullable=false) DepartmentRow destination;
    @Column(name="destination_name",nullable=false,length=255) String destinationName;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="creator_id",nullable=false,length=36) UUID creatorId;
    @Column(name="creator_name",nullable=false,length=100) String creatorName;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="creator_department_id",length=36) UUID creatorDepartmentId;
    @Column(nullable=false,length=20) String priority;
    @Column(nullable=false,length=30) String status;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="assigned_operator_id",length=36) UUID assignedOperatorId;
    @Column(name="assigned_operator_name",length=100) String assignedOperatorName;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="epochSecond",column=@Column(name="created_second",nullable=false)),
        @AttributeOverride(name="nano",column=@Column(name="created_nano",nullable=false))}) StoredInstant createdAt;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="epochSecond",column=@Column(name="scheduled_second")),
        @AttributeOverride(name="nano",column=@Column(name="scheduled_nano"))}) StoredInstant scheduledAt;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="epochSecond",column=@Column(name="first_assigned_second")),
        @AttributeOverride(name="nano",column=@Column(name="first_assigned_nano"))}) StoredInstant firstAssignedAt;
    @Embedded @AttributeOverrides({
        @AttributeOverride(name="epochSecond",column=@Column(name="completed_second")),
        @AttributeOverride(name="nano",column=@Column(name="completed_nano"))}) StoredInstant completedAt;
    @Column(name="event_count",nullable=false) int eventCount;
    @OneToMany(mappedBy="ticket",cascade=CascadeType.ALL)
    @OrderBy("id.eventIndex ASC") List<TicketEventRow> events=new ArrayList<>();
    protected TicketRow() {}
}
