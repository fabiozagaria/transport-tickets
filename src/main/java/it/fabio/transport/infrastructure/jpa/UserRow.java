package it.fabio.transport.infrastructure.jpa;

import it.fabio.transport.domain.*;
import it.fabio.transport.security.UserAccount;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity @Table(name="app_users")
public class UserRow {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) UUID id;
    @Column(nullable=false,length=100) String username;
    @Column(name="password_hash",nullable=false,length=255) String passwordHash;
    @Column(nullable=false,length=20) String role;
    @Column(nullable=false) boolean enabled;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="department_id") DepartmentRow department;
    @Version @Column(nullable=false) Long revision;
    protected UserRow() {}
    User domain() { return new User(id,username,UserRole.valueOf(role),department==null ? null : department.getId()); }
    UserAccount account() { return new UserAccount(domain(),passwordHash,enabled); }
}
