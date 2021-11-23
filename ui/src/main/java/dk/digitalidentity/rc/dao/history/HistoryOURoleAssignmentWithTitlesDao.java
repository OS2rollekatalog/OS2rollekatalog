package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;

public interface HistoryOURoleAssignmentWithTitlesDao extends JpaRepository<HistoryOURoleAssignmentWithTitles, Long> {

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithTitles hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3")
    List<HistoryOURoleAssignmentWithTitles> findByDate(Integer year, Integer month, Integer day);

    @Query("SELECT hra FROM HistoryOURoleAssignmentWithTitles hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3 AND hra.roleItSystemId IN ?4")
    List<HistoryOURoleAssignmentWithTitles> findByDateAndItSystems(Integer year, Integer month, Integer day, List<Long> itSystemIds);
}
