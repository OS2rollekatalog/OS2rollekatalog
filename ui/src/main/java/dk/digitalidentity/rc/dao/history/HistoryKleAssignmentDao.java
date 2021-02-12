package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;

public interface HistoryKleAssignmentDao extends JpaRepository<HistoryKleAssignment, Long> {
	
	/*
    @Query("SELECT hka FROM HistoryKleAssignment hka WHERE YEAR(hka.dato)=?1 AND MONTH(hka.dato)=?2 AND DAY(hka.dato)=?3")
    List<HistoryKleAssignment> findByDate(Integer year, Integer month, Integer day);
    
    @Query("SELECT hka FROM HistoryKleAssignment hka WHERE YEAR(hka.dato)=?1 AND MONTH(hka.dato)=?2 AND DAY(hka.dato)=?3 AND hka.userUuid=?4")
    List<HistoryKleAssignment> findByDateAndUserUuid(Integer year, Integer month, Integer day, String userUuid);
	 */
	
    @Query(nativeQuery = true, value = "SELECT * FROM history_kle_assignments hka WHERE hka.dato =?1")
    List<HistoryKleAssignment> findByDate(String date);
    
    @Query(nativeQuery = true, value = "SELECT * FROM history_kle_assignments hka WHERE hka.dato=?1 AND hka.user_uuid=?2")
    List<HistoryKleAssignment> findByDateAndUserUuid(String date, String userUuid);
}
