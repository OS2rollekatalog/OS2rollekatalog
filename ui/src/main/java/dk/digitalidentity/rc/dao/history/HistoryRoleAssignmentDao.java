package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;

public interface HistoryRoleAssignmentDao extends JpaRepository<HistoryRoleAssignment, Long> {

    @Query("SELECT hra FROM HistoryRoleAssignment hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3")
    List<HistoryRoleAssignment> findByDate(Integer year, Integer month, Integer day);

    @Query("SELECT hra FROM HistoryRoleAssignment hra WHERE YEAR(hra.dato)=?1 AND MONTH(hra.dato)=?2 AND DAY(hra.dato)=?3 AND hra.roleItSystemId IN ?4")
    List<HistoryRoleAssignment> findByDateAndItSystems(Integer year, Integer month, Integer day, List<Long> itSystemIds);
}
