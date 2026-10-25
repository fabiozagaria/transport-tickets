package it.fabio.transport.infrastructure.jpa;

import it.fabio.transport.domain.Department;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity @Table(name="departments")
public class DepartmentRow {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) UUID id;
    @Column(nullable=false,length=255) String name;
    @Version @Column(nullable=false) Long revision;
    public UUID getId() { return id; }
    protected DepartmentRow() {}
    DepartmentRow(Department department) { id=department.id();name=department.name(); }
    Department domain() { return new Department(id,name); }
}
