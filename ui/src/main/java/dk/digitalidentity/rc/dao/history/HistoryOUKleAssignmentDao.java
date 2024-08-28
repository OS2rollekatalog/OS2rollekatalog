package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;

public interface HistoryOUKleAssignmentDao extends JpaRepository<HistoryOUKleAssignment, Long> {
	
    @Query("SELECT hka FROM HistoryOUKleAssignment hka WHERE hka.dato=?1")
    List<HistoryOUKleAssignment> findByDate(LocalDate date);
    
    @Query("SELECT hka FROM HistoryOUKleAssignment hka WHERE hka.dato=?1 AND hka.ouUuid=?2")
    List<HistoryOUKleAssignment> findByDateAndOuUuid(LocalDate date, String ouUuid);

}
