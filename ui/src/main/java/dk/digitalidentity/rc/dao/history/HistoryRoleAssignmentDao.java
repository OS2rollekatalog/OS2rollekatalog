package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import org.springframework.data.repository.query.Param;

public interface HistoryRoleAssignmentDao extends JpaRepository<HistoryRoleAssignment, Long> {

    @Query("SELECT hra FROM HistoryRoleAssignment hra WHERE hra.dato=:dato")
    Stream<HistoryRoleAssignment> streamByDate(@Param("dato") final LocalDate dato);

    @Query("SELECT hra FROM HistoryRoleAssignment hra WHERE hra.dato=:dato AND hra.roleItSystemId IN :itSystemIds")
    List<HistoryRoleAssignment> findByDateAndItSystems(@Param("dato") final LocalDate dato, @Param("itSystemIds") final List<Long> itSystemIds);
}
