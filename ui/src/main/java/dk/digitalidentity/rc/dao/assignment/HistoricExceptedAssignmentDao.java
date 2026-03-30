package dk.digitalidentity.rc.dao.assignment;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;

public interface HistoricExceptedAssignmentDao extends JpaRepository<HistoricExceptedAssignment, Long> {
	Set<HistoricExceptedAssignment> findByExceptionUserUuid(String userUuid);

	Set<HistoricExceptedAssignment> findAllByRecordHashIn(Collection<String> recordHashes);

	@Query("SELECT ha FROM HistoricExceptedAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay)")
	List<HistoricExceptedAssignment> findActiveAtDate(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay);

	@Query("SELECT ha FROM HistoricExceptedAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay) " +
		"AND ha.exceptionItSystemId IN :itSystemIds")
	List<HistoricExceptedAssignment> findActiveAtDateAndItSystemIdIn(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay,
		@Param("itSystemIds") Collection<Long> itSystemIds);
}
