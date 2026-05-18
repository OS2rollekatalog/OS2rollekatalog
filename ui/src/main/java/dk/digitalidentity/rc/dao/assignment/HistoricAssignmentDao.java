package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public interface HistoricAssignmentDao extends JpaRepository<HistoricAssignment, Long> {
	Set<HistoricAssignment> findByUserUuid(String userUuid);

	Set<HistoricAssignment> findAllByRecordHashIn(Collection<String> recordHashes);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE HistoricAssignment ha SET ha.validTo = :validTo WHERE ha.recordHash IN :recordHashes AND ha.validTo IS NULL")
	void updateValidToByRecordHashIn(@org.springframework.data.repository.query.Param("recordHashes") Collection<String> recordHashes,
	                                 @org.springframework.data.repository.query.Param("validTo") java.time.LocalDateTime validTo);

	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay)")
	List<HistoricAssignment> findActiveAtDate(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay);

	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay) " +
		"AND ha.itSystemId IN :itSystemIds")
	List<HistoricAssignment> findActiveAtDateAndItSystemIdIn(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay,
		@Param("itSystemIds") Collection<Long> itSystemIds);

	/**
	 * Bulk projection of all constraints for assignments active at the given date.
	 * Returns [assignmentId, constraintTypeName, value] — no lazy loading during streaming.
	 */
	@Query("SELECT hac.historicAssignment.id, hac.constraintTypeName, hac.value " +
		"FROM HistoricAssignmentConstraint hac " +
		"WHERE hac.historicAssignment.validFrom <= :endOfDay " +
		"AND (hac.historicAssignment.validTo IS NULL OR hac.historicAssignment.validTo >= :startOfDay)")
	List<Object[]> findConstraintProjectionsForDate(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay);

	@Query("SELECT hac.historicAssignment.id, hac.constraintTypeName, hac.value " +
		"FROM HistoricAssignmentConstraint hac " +
		"WHERE hac.historicAssignment.validFrom <= :endOfDay " +
		"AND (hac.historicAssignment.validTo IS NULL OR hac.historicAssignment.validTo >= :startOfDay) " +
		"AND hac.historicAssignment.itSystemId IN :itSystemIds")
	List<Object[]> findConstraintProjectionsForDateAndItSystems(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay,
		@Param("itSystemIds") Collection<Long> itSystemIds);

	/** Lightweight projection returning [userUuid, itSystemName, userRoleId, startDate, stopDate] — used for weight pre-computation. */
	@Query("SELECT ha.userUuid, ha.itSystemName, ha.userRoleId, ha.startDate, ha.stopDate FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay)")
	List<Object[]> findWeightTriples(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay);

	@Query("SELECT ha.userUuid, ha.itSystemName, ha.userRoleId, ha.startDate, ha.stopDate FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay) " +
		"AND ha.itSystemId IN :itSystemIds")
	List<Object[]> findWeightTriplesForItSystems(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay,
		@Param("itSystemIds") Collection<Long> itSystemIds);

	/** Streams assignments using a server-side cursor — caller must be @Transactional and close the stream. */
	@QueryHints({
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_READ_ONLY, value = "true")
	})
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay)")
	Stream<HistoricAssignment> streamActiveAtDate(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay);

	@QueryHints({
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_READ_ONLY, value = "true")
	})
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :endOfDay " +
		"AND (ha.validTo IS NULL OR ha.validTo >= :startOfDay) " +
		"AND ha.itSystemId IN :itSystemIds")
	Stream<HistoricAssignment> streamActiveAtDateAndItSystemIdIn(
		@Param("startOfDay") LocalDateTime startOfDay,
		@Param("endOfDay") LocalDateTime endOfDay,
		@Param("itSystemIds") Collection<Long> itSystemIds);

	// ── Attestation queries ────────────────────────────────────────────────────

	/** All assignments valid at {@code validAt} whose responsible OU is {@code responsibleOuUuid}. */
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :validAt AND (ha.validTo > :validAt OR ha.validTo IS NULL) " +
		"AND ha.responsibleOUUuid = :responsibleOuUuid")
	List<HistoricAssignment> listValidAssignmentsByResponsibleOu(
		@Param("validAt") LocalDateTime validAt,
		@Param("responsibleOuUuid") String responsibleOuUuid);

	/**
	 * Direct assignments for {@code userUuid} at {@code validAt} where the responsible OU
	 * is NOT {@code responsibleOuUuid}. Used to surface cross-department assignments.
	 */
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :validAt AND (ha.validTo > :validAt OR ha.validTo IS NULL) " +
		"AND ha.responsibleOUUuid IS NOT NULL AND ha.responsibleOUUuid <> :responsibleOuUuid " +
		"AND ha.userUuid = :userUuid")
	List<HistoricAssignment> listValidAssignmentsForUserWhereResponsibleOUIsNot(
		@Param("validAt") LocalDateTime validAt,
		@Param("userUuid") String userUuid,
		@Param("responsibleOuUuid") String responsibleOuUuid);

	/** Assignments for {@code userUuid} at {@code validAt} that have an IT-system responsible (not OU). */
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :validAt AND (ha.validTo > :validAt OR ha.validTo IS NULL) " +
		"AND ha.responsibleUserUuid IS NOT NULL AND ha.userUuid = :userUuid")
	List<HistoricAssignment> listValidAssignmentsForUserHandledByItSystemResponsible(
		@Param("validAt") LocalDateTime validAt,
		@Param("userUuid") String userUuid);

	/**
	 * Assignments that first became valid between {@code fromDate} (exclusive) and {@code toDate} (exclusive)
	 * for the given responsible OU. Used to show "new since last attestation" changes.
	 */
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom > :fromDate AND ha.validFrom < :toDate " +
		"AND ha.responsibleOUUuid = :responsibleOuUuid")
	List<HistoricAssignment> listAssignmentsWhichHaveBeenValidBetweenByResponsibleOu(
		@Param("fromDate") LocalDateTime fromDate,
		@Param("toDate") LocalDateTime toDate,
		@Param("responsibleOuUuid") String responsibleOuUuid);

	/** All assignments valid at {@code validAt} whose responsible user is {@code responsibleUserUuid}. */
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :validAt AND (ha.validTo > :validAt OR ha.validTo IS NULL) " +
		"AND ha.responsibleUserUuid = :responsibleUserUuid")
	List<HistoricAssignment> listValidAssignmentsByResponsibleUserUuid(
		@Param("validAt") LocalDateTime validAt,
		@Param("responsibleUserUuid") String responsibleUserUuid);

	/** Assignments valid at {@code validAt} for a given responsible user and IT-system. */
	@Query("SELECT ha FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :validAt AND (ha.validTo > :validAt OR ha.validTo IS NULL) " +
		"AND ha.responsibleUserUuid = :responsibleUserUuid AND ha.itSystemId = :itSystemId")
	List<HistoricAssignment> listValidAssignmentsByResponsibleUserUuidAndItSystemId(
		@Param("validAt") LocalDateTime validAt,
		@Param("responsibleUserUuid") String responsibleUserUuid,
		@Param("itSystemId") Long itSystemId);

	/**
	 * Returns one representative row per logical assignment (responsibleUserUuid, userUuid, sensitiveRole, itSystemId)
	 * that is valid at {@code validAt}, for assignments managed by an IT-system responsible.
	 * <p>
	 * Because {@code historic_assignment} is a temporal table, the same logical assignment can have multiple rows
	 * over time (one per revision). Grouping collapses these into one entry per unique logical assignment,
	 * and MAX(id) picks the most recently written row within the valid window.
	 * Without this, the tracker would create duplicate attestations for the same logical assignment.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ha2.* FROM historic_assignment ha2 " +
		"INNER JOIN (SELECT max(ha.id) AS sid FROM historic_assignment ha " +
		"  WHERE ha.valid_from <= :validAt AND (ha.valid_to > :validAt OR ha.valid_to IS NULL) " +
		"  AND ha.responsible_user_uuid IS NOT NULL " +
		"  GROUP BY ha.responsible_user_uuid, ha.user_uuid, ha.sensitive_role, ha.it_system_id) AS sub ON sub.sid = ha2.id")
	List<HistoricAssignment> findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem(
		@Param("validAt") LocalDateTime validAt);

	/**
	 * Returns one representative row per logical assignment (responsibleOUUuid, userUuid, sensitiveRole, assignedThroughType)
	 * that is valid at {@code validAt}, for assignments managed by an OU responsible.
	 * <p>
	 * See {@link #findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem} for why grouping is needed.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ha2.* FROM historic_assignment ha2 " +
		"INNER JOIN (SELECT max(ha.id) AS sid FROM historic_assignment ha " +
		"  WHERE ha.valid_from <= :validAt AND (ha.valid_to > :validAt OR ha.valid_to IS NULL) " +
		"  AND ha.responsible_ou_uuid IS NOT NULL " +
		"  GROUP BY ha.responsible_ou_uuid, ha.user_uuid, ha.sensitive_role, ha.assigned_through_type) AS sub ON sub.sid = ha2.id")
	List<HistoricAssignment> findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(
		@Param("validAt") LocalDateTime validAt);

	/**
	 * Returns one representative row per (responsibleUserUuid, sensitiveRole) group valid at {@code validAt}.
	 * Coarser grouping than {@link #findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem}
	 * — used where only the responsible and sensitivity matter, not the individual user or IT-system.
	 * <p>
	 * See {@link #findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem} for why grouping is needed.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ha2.* FROM historic_assignment ha2 " +
		"INNER JOIN (SELECT max(ha.id) AS sid FROM historic_assignment ha " +
		"  WHERE ha.valid_from <= :validAt AND (ha.valid_to > :validAt OR ha.valid_to IS NULL) " +
		"  AND ha.responsible_user_uuid IS NOT NULL " +
		"  GROUP BY ha.responsible_user_uuid, ha.sensitive_role) AS sub ON sub.sid = ha2.id")
	List<HistoricAssignment> findValidGroupByResponsibleUserUuidAndSensitiveRole(
		@Param("validAt") LocalDateTime validAt);

	/**
	 * Returns one representative row per (responsibleOUUuid, sensitiveRole) group valid at {@code validAt}.
	 * Coarser grouping than {@link #findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole}
	 * — used where only the responsible OU and sensitivity matter.
	 * <p>
	 * See {@link #findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem} for why grouping is needed.
	 */
	@Query(nativeQuery = true, value =
		"SELECT ha2.* FROM historic_assignment ha2 " +
		"INNER JOIN (SELECT max(ha.id) AS sid FROM historic_assignment ha " +
		"  WHERE ha.valid_from <= :validAt AND (ha.valid_to > :validAt OR ha.valid_to IS NULL) " +
		"  AND ha.responsible_ou_uuid IS NOT NULL " +
		"  GROUP BY ha.responsible_ou_uuid, ha.sensitive_role) AS sub ON sub.sid = ha2.id")
	List<HistoricAssignment> findValidGroupByResponsibleOuUuidAndSensitiveRole(
		@Param("validAt") LocalDateTime validAt);

	/**
	 * IDs of assignments valid at any point between {@code from} and {@code to} for a given IT-system.
	 * Ordered for deterministic paging.
	 */
	@Query("SELECT ha.id FROM HistoricAssignment ha " +
		"WHERE ha.itSystemId = :itSystemId " +
		"AND ha.validFrom <= :to AND (ha.validTo > :from OR ha.validTo IS NULL) " +
		"ORDER BY ha.userUuid ASC, ha.updatedAt DESC")
	List<Long> listAssignmentValidBetweenForItSystem(
		@Param("itSystemId") Long itSystemId,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to);

	/**
	 * IDs of assignments for a given responsible OU that were valid at any point between {@code from} and {@code to}.
	 */
	@Query("SELECT ha.id FROM HistoricAssignment ha " +
		"WHERE ha.responsibleOUUuid = :ouUuid " +
		"AND ha.validFrom <= :to AND (ha.validTo > :from OR ha.validTo IS NULL) " +
		"ORDER BY ha.userUuid ASC, ha.updatedAt DESC")
	List<Long> listAssignmentIdsValidBetweenForRoleOu(
		@Param("ouUuid") String ouUuid,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to);

	/** IDs of all assignments valid at any point between {@code from} and {@code to}. */
	@Query("SELECT ha.id FROM HistoricAssignment ha " +
		"WHERE ha.validFrom <= :to AND (ha.validTo > :from OR ha.validTo IS NULL) " +
		"ORDER BY ha.userUuid ASC, ha.updatedAt DESC")
	List<Long> listAssignmentIdsValidBetween(
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to);

	/** Fetch full entities for the given IDs. Used for paginated DTO projection. */
	List<HistoricAssignment> findByIdIn(List<Long> ids);
}
