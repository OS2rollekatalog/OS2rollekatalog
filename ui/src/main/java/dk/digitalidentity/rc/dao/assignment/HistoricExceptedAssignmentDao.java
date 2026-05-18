package dk.digitalidentity.rc.dao.assignment;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;

public interface HistoricExceptedAssignmentDao extends JpaRepository<HistoricExceptedAssignment, Long> {
	Set<HistoricExceptedAssignment> findByExceptionUserUuid(String userUuid);

	Set<HistoricExceptedAssignment> findAllByRecordHashIn(Collection<String> recordHashes);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE HistoricExceptedAssignment ha SET ha.validTo = :validTo WHERE ha.recordHash IN :recordHashes AND ha.validTo IS NULL")
	void updateValidToByRecordHashIn(@org.springframework.data.repository.query.Param("recordHashes") Collection<String> recordHashes,
	                                 @org.springframework.data.repository.query.Param("validTo") java.time.LocalDateTime validTo);

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

	/** Streams excepted assignments using a server-side cursor — caller must be @Transactional and close the stream. */
	@QueryHints({
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_READ_ONLY, value = "true")
	})
	@Query("SELECT ha FROM HistoricExceptedAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay)")
	Stream<HistoricExceptedAssignment> streamActiveAtDate(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay);

	@QueryHints({
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_READ_ONLY, value = "true")
	})
	@Query("SELECT ha FROM HistoricExceptedAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay) " +
		"AND ha.exceptionItSystemId IN :itSystemIds")
	Stream<HistoricExceptedAssignment> streamActiveAtDateAndItSystemIdIn(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay,
		@Param("itSystemIds") Collection<Long> itSystemIds);
}
