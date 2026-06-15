package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoricAssignmentAttestationService {

	private final HistoricAssignmentDao historicAssignmentDao;

	/**
	 * Converts a LocalDate snapshot to the end-of-day LocalDateTime used for temporal queries.
	 */
	private static LocalDateTime toValidAt(final LocalDate date) {
		// Use start of the *next* day so that any assignment created during `date` is included,
		// and any assignment with validTo set to midnight of the next day is still considered active.
		return date.plusDays(1).atStartOfDay();
	}

	/**
	 * Converts a LocalDate to a LocalDateTime boundary for "validFrom > fromDate" comparisons.
	 */
	private static LocalDateTime toStartOfDay(final LocalDate date) {
		return date.atStartOfDay();
	}

	/**
	 * Returns all user role assignments valid on {@code validAt} whose responsible OU is
	 * {@code responsibleOuUuid}.
	 */
	public List<AttestationUserRoleAssignment> listValidAssignmentsByResponsibleOu(final LocalDate validAt, final String responsibleOuUuid) {
		return historicAssignmentDao
			.listValidAssignmentsByResponsibleOu(toValidAt(validAt), responsibleOuUuid)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns assignments for {@code userUuid} valid on {@code validAt} where the responsible OU
	 * is not {@code responsibleOuUuid}. Used to surface cross-department role assignments.
	 */
	public List<AttestationUserRoleAssignment> listValidAssignmentsForUserWhereResponsibleOUIsNot(final LocalDate validAt, final String userUuid, final String responsibleOuUuid) {
		return historicAssignmentDao
			.listValidAssignmentsForUserWhereResponsibleOUIsNot(toValidAt(validAt), userUuid, responsibleOuUuid)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns assignments for {@code userUuid} valid on {@code validAt} that are managed by an
	 * IT-system responsible rather than an OU manager.
	 */
	public List<AttestationUserRoleAssignment> listValidAssignmentsForUserHandledByItSystemResponsible(final LocalDate validAt, final String userUuid) {
		return historicAssignmentDao
			.listValidAssignmentsForUserHandledByItSystemResponsible(toValidAt(validAt), userUuid)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns assignments that first became valid strictly between {@code fromDate} and
	 * {@code toDate} for the given responsible OU. Used to show new assignments since the
	 * last attestation.
	 */
	public List<AttestationUserRoleAssignment> listAssignmentsWhichHaveBeenValidBetweenByResponsibleOu(final LocalDate fromDate, final LocalDate toDate, final String responsibleOuUuid) {
		return historicAssignmentDao
			.listAssignmentsWhichHaveBeenValidBetweenByResponsibleOu(
				toStartOfDay(fromDate), toStartOfDay(toDate), responsibleOuUuid)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns all assignments valid on {@code validAt} whose responsible collection is
	 * {@code responsibleCollectionId}.
	 */
	public List<AttestationUserRoleAssignment> listValidAssignmentsByResponsibleCollectionId(final LocalDate validAt, final Long responsibleCollectionId) {
		return historicAssignmentDao
			.listValidAssignmentsByResponsibleCollectionId(toValidAt(validAt), responsibleCollectionId)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns all assignments valid on {@code validAt} for a given responsible collection and IT-system.
	 */
	public List<AttestationUserRoleAssignment> listValidAssignmentsByResponsibleCollectionIdAndItSystemId(final LocalDate validAt, final Long responsibleCollectionId, final Long itSystemId) {
		return historicAssignmentDao
			.listValidAssignmentsByResponsibleCollectionIdAndItSystemId(toValidAt(validAt), responsibleCollectionId, itSystemId)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	// ── Report service queries ─────────────────────────────────────────────────

	/**
	 * Returns the IDs of all assignments valid at any point between {@code from} and {@code to}.
	 * Used for paginated report generation.
	 */
	public List<Long> listAssignmentIdsValidBetween(final LocalDate from, final LocalDate to) {
		return historicAssignmentDao.listAssignmentIdsValidBetween(
			toStartOfDay(from), toStartOfDay(to));
	}

	/**
	 * Returns the IDs of assignments valid between {@code from} and {@code to} for the given
	 * responsible OU. Used for paginated OU-scoped report generation.
	 */
	public List<Long> listAssignmentIdsValidBetweenForRoleOu(final String ouUuid, final LocalDate from, final LocalDate to) {
		return historicAssignmentDao.listAssignmentIdsValidBetweenForRoleOu(
			ouUuid, toStartOfDay(from), toStartOfDay(to));
	}

	/**
	 * Returns the IDs of assignments valid between {@code from} and {@code to} for the given
	 * IT-system. Used for paginated IT-system-scoped report generation.
	 */
	public List<Long> listAssignmentValidBetweenForItSystem(final Long itSystemId, final LocalDate from, final LocalDate to) {
		return historicAssignmentDao.listAssignmentValidBetweenForItSystem(
			itSystemId, toStartOfDay(from), toStartOfDay(to));
	}

	/**
	 * Fetches immutable DTO projections for the given IDs.
	 * Used to materialise a page of report rows after ID-based pagination.
	 * Returns {@link AttestationUserRoleAssignmentDto} (a plain value object) rather than
	 * an entity, so callers have no Hibernate session attachment and no dirty-checking overhead.
	 */
	public List<AttestationUserRoleAssignmentDto> findByIdIn(final List<Long> ids) {
		return historicAssignmentDao.findByIdIn(ids)
			.stream()
			.map(HistoricAssignmentAttestationMapper::toDto)
			.toList();
	}

	/**
	 * Returns one representative assignment per (responsibleCollectionId, userUuid, inherit,
	 * sensitiveRole, itSystemId) group, for IT-system-responsible assignments valid on
	 * {@code validAt}. Used by the tracker to determine which IT-system attestations to create.
	 */
	public List<AttestationUserRoleAssignment> findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(final LocalDate validAt) {
		return historicAssignmentDao
			.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(toValidAt(validAt))
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns one representative assignment per (responsibleOUUuid, userUuid, inherit,
	 * sensitiveRole, assignedThroughType) group, for OU-responsible assignments valid on
	 * {@code validAt}. Used by the tracker to determine which organisation attestations to create.
	 */
	public List<AttestationUserRoleAssignment> findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(final LocalDate validAt) {
		return historicAssignmentDao
			.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(toValidAt(validAt))
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns one representative assignment per (responsibleCollectionId, sensitiveRole) group,
	 * for IT-system-responsible assignments valid on {@code validAt}.
	 */
	public List<AttestationUserRoleAssignment> findValidGroupByResponsibleCollectionIdAndSensitiveRole(final LocalDate validAt) {
		return historicAssignmentDao
			.findValidGroupByResponsibleCollectionIdAndSensitiveRole(toValidAt(validAt))
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}

	/**
	 * Returns one representative assignment per (responsibleOUUuid, sensitiveRole) group,
	 * for OU-responsible assignments valid on {@code validAt}.
	 * Mirrors {@code AttestationOuAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole}.
	 */
	public List<AttestationUserRoleAssignment> findValidGroupByResponsibleOuUuidAndSensitiveRole(final LocalDate validAt) {
		return historicAssignmentDao
			.findValidGroupByResponsibleOuUuidAndSensitiveRole(toValidAt(validAt))
			.stream()
			.map(HistoricAssignmentAttestationMapper::toAttestationAssignment)
			.toList();
	}
}
