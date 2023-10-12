package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;

public interface HistoryOURoleAssignmentDao extends JpaRepository<HistoryOURoleAssignment, Long> {

    @Query("SELECT hra FROM HistoryOURoleAssignment hra WHERE hra.dato=?1")
    List<HistoryOURoleAssignment> findByDate(final LocalDate when);

    @Query("SELECT hra FROM HistoryOURoleAssignment hra WHERE hra.dato=?1")
    Stream<HistoryOURoleAssignment> streamByDate(final LocalDate when);

    @Query("SELECT hra FROM HistoryOURoleAssignment hra WHERE hra.dato=?1 AND hra.roleItSystemId IN ?2")
    List<HistoryOURoleAssignment> findByDateAndItSystems(final LocalDate when, final List<Long> itSystemIds);
}
