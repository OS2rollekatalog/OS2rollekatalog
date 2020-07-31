package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;

public interface HistoryOUKleAssignmentDao extends JpaRepository<HistoryOUKleAssignment, Long> {
	
    @Query("SELECT hka FROM HistoryOUKleAssignment hka WHERE YEAR(hka.dato)=?1 AND MONTH(hka.dato)=?2 AND DAY(hka.dato)=?3")
    List<HistoryOUKleAssignment> findByDate(Integer year, Integer month, Integer day);
    
    @Query("SELECT hka FROM HistoryOUKleAssignment hka WHERE YEAR(hka.dato)=?1 AND MONTH(hka.dato)=?2 AND DAY(hka.dato)=?3 AND hka.ouUuid=?4")
    List<HistoryOUKleAssignment> findByDateAndOuUuid(Integer year, Integer month, Integer day, String ouUuid);

}
