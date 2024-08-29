package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;

public interface HistoryOURoleAssignmentWithTitlesDao extends JpaRepository<HistoryOURoleAssignmentWithTitles, Long> {

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithTitles hra WHERE hra.dato=?1")
    Stream<HistoryOURoleAssignmentWithTitles> streamByDate(final LocalDate when);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithTitles hra WHERE hra.dato=?1")
    List<HistoryOURoleAssignmentWithTitles> findByDate(LocalDate date);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithTitles hra WHERE hra.dato=?1 AND hra.roleItSystemId IN ?2")
    List<HistoryOURoleAssignmentWithTitles> findByDateAndItSystems(LocalDate date, List<Long> itSystemIds);
}
