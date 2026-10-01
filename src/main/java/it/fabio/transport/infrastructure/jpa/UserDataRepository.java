package it.fabio.transport.infrastructure.jpa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserDataRepository extends JpaRepository<UserRow,UUID> {
    Optional<UserRow> findByUsername(String username);
    List<UserRow> findByEnabledTrueOrderByUsernameAsc();
}
