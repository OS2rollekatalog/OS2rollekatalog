package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HistoricItSystemAssignmentDao extends JpaRepository<HistoricItSystemAssignment, Long> {
	@Modifying
	@Query("UPDATE HistoricItSystemAssignment h SET h.validTo = :validTo WHERE h.recordHash = :recordHash AND h.validTo IS NULL")
	void closeOpenByRecordHash(@Param("recordHash") String recordHash, @Param("validTo") LocalDateTime validTo);

	boolean existsByRecordHashAndValidToIsNull(String recordHash);

	List<HistoricItSystemAssignment> findByItSystemIdAndValidToIsNull(Long itSystemId);

	/**
	 * Keyset-pagineret liste af åbne rækker for it-systemer der har en responsible-collection.
	 * Bruges af engangs-reparations-tasken; recordHash kan kun genberegnes i Java, så kandidaterne
	 * pages med {@code id > lastId} og repareres række for række.
	 */
	@Query("SELECT h.id FROM HistoricItSystemAssignment h " +
		"WHERE h.validTo IS NULL AND h.id > :lastId " +
		"AND EXISTS (SELECT 1 FROM AttestationResponsibleCollection c WHERE c.itSystemId = h.itSystemId) " +
		"ORDER BY h.id")
	List<Long> findOpenIdsForItSystemsWithResponsibleCollection(@Param("lastId") long lastId, Pageable pageable);
}
