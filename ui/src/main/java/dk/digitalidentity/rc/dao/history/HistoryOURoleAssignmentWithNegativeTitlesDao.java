package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

public interface HistoryOURoleAssignmentWithNegativeTitlesDao extends JpaRepository<HistoryOURoleAssignmentWithNegativeTitles, Long> {
    @Query("SELECT hra FROM HistoryOURoleAssignmentWithNegativeTitles hra WHERE hra.dato=?1")
    Stream<HistoryOURoleAssignmentWithNegativeTitles> streamByDate(final LocalDate when);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithNegativeTitles hra WHERE hra.dato=?1")
    List<HistoryOURoleAssignmentWithNegativeTitles> findByDate(LocalDate date);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithNegativeTitles hra WHERE hra.dato=?1 AND hra.roleItSystemId IN ?2")
    List<HistoryOURoleAssignmentWithNegativeTitles> findByDateAndItSystems(LocalDate date, List<Long> itSystemIds);
}
