package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface HistoricItSystemAssignmentDao extends JpaRepository<HistoricItSystemAssignment, Long> {
	@Modifying
	@Query("UPDATE HistoricItSystemAssignment h SET h.validTo = :validTo WHERE h.recordHash = :recordHash AND h.validTo IS NULL")
	void closeOpenByRecordHash(@Param("recordHash") String recordHash, @Param("validTo") LocalDateTime validTo);
}
