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

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithExceptions hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3")
    List<HistoryOURoleAssignmentWithExceptions> findByDate(Integer year, Integer month, Integer day);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithExceptions hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3 AND hra.roleItSystemId IN ?4")
    List<HistoryOURoleAssignmentWithExceptions> findByDateAndItSystems(Integer year, Integer month, Integer day, List<Long> itSystemIds);
}
