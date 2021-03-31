package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryTitleRoleAssignment;

public interface HistoryTitleRoleAssignmentDao extends JpaRepository<HistoryOURoleAssignment, Long> {

    @Query("SELECT hra FROM HistoryTitleRoleAssignment hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3")
    List<HistoryTitleRoleAssignment> findByDate(Integer year, Integer month, Integer day);

    @Query("SELECT hra FROM HistoryTitleRoleAssignment hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3 AND hra.roleItSystemId IN ?4")
    List<HistoryTitleRoleAssignment> findByDateAndItSystems(Integer year, Integer month, Integer day, List<Long> itSystemIds);
}
