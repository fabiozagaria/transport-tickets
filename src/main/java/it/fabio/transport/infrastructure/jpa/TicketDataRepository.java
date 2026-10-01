package it.fabio.transport.infrastructure.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface TicketDataRepository extends JpaRepository<TicketRow,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketRow t where t.id = :id") Optional<TicketRow> findForUpdate(@Param("id") UUID id);
    @Query("select t.revision from TicketRow t where t.id = :id") Optional<Long> findRevision(@Param("id") UUID id);
}
