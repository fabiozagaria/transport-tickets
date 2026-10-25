package it.fabio.transport.infrastructure.jpa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface DepartmentDataRepository extends JpaRepository<DepartmentRow,UUID> {}
