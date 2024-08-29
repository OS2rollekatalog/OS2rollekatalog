package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;

public interface HistoryOURoleAssignmentWithExceptionsDao extends JpaRepository<HistoryOURoleAssignmentWithExceptions, Long> {

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithExceptions hra WHERE hra.dato=?1")
    Stream<HistoryOURoleAssignmentWithExceptions> streamByDate(final LocalDate localDate);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithExceptions hra WHERE hra.dato=?1")
    List<HistoryOURoleAssignmentWithExceptions> findByDate(LocalDate date);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithExceptions hra WHERE hra.dato=?1 AND hra.roleItSystemId IN ?2")
    List<HistoryOURoleAssignmentWithExceptions> findByDateAndItSystems(LocalDate date, List<Long> itSystemIds);
}
