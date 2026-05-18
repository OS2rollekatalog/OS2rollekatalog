package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignmentConstraint;
import dk.digitalidentity.rc.dao.serializer.SystemRoleAssignmentDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoricItSystemAssignmentService {

	private final HistoricItSystemAssignmentDao dao;
	private final SystemRoleAssignmentDao systemRoleAssignmentDao;

	@Transactional
	public void recordSystemRoleAssignmentAdded(UserRole userRole, SystemRoleAssignment assignment) {
		dao.save(toHistoric(userRole, assignment));
	}

	@Transactional
	public void recordSystemRoleAssignmentRemoved(UserRole userRole, SystemRoleAssignment assignment) {
		HistoricItSystemAssignment toClose = toHistoric(userRole, assignment);
		dao.closeOpenByRecordHash(toClose.getRecordHash(), LocalDateTime.now());
	}

	/**
	 * Lukker den åbne historic-række med {@code preEditHash} og åbner en ny række der afspejler
	 * den nuværende state af assignment. Bruges af constraint-edit-stierne hvor SRA muteres
	 * in-place og hash dermed skifter — preEditHash skal beregnes FØR mutationen via
	 * {@link #computeRecordHash(UserRole, SystemRoleAssignment)}.
	 */
	@Transactional
	public void recordSystemRoleAssignmentEdited(UserRole userRole, SystemRoleAssignment assignment, String preEditHash) {
		HistoricItSystemAssignment newRecord = toHistoric(userRole, assignment);
		if (newRecord.getRecordHash().equals(preEditHash)) {
			return;
		}
		dao.closeOpenByRecordHash(preEditHash, LocalDateTime.now());
		dao.save(newRecord);
	}

	/**
	 * Idempotent variant der kun indsætter en ny åben række hvis der ikke allerede findes
	 * én med samme hash. Bruges af engangs-seed-tasken.
	 */
	@Transactional
	public void recordSystemRoleAssignmentSeedIfMissing(UserRole userRole, SystemRoleAssignment assignment) {
		HistoricItSystemAssignment record = toHistoric(userRole, assignment);
		if (dao.existsByRecordHashAndValidToIsNull(record.getRecordHash())) {
			return;
		}
		dao.save(record);
	}

	/**
	 * Loader SRA + UserRole + ItSystem og delegerer til {@link #recordSystemRoleAssignmentSeedIfMissing}.
	 * Egen {@code @Transactional} sørger for at lazy-properties på UserRole (fx itSystem) kan
	 * tilgås — kaldere som seed-tasken har ingen tx-grænse omkring deres chunk-loop og ville
	 * ellers ramme LazyInitializationException når UserRole-proxy'en hydreres.
	 *
	 * @return true hvis en ny historic-række blev indsat eller allerede findes; false hvis SRA
	 *         er væk, mangler UserRole eller UserRole mangler ItSystem (rolle-katalog-roller har
	 *         ikke et IT-system).
	 */
	@Transactional
	public boolean seedHistoricRowFromSystemRoleAssignmentId(long sraId) {
		SystemRoleAssignment sra = systemRoleAssignmentDao.findById(sraId).orElse(null);
		if (sra == null || sra.getUserRole() == null || sra.getUserRole().getItSystem() == null) {
			return false;
		}
		recordSystemRoleAssignmentSeedIfMissing(sra.getUserRole(), sra);
		return true;
	}

	public String computeRecordHash(UserRole userRole, SystemRoleAssignment assignment) {
		return toHistoric(userRole, assignment).getRecordHash();
	}

	private HistoricItSystemAssignment toHistoric(UserRole userRole, SystemRoleAssignment assignment) {
		ItSystem itSystem = userRole.getItSystem();

		List<HistoricItSystemAssignmentConstraint> constraints = buildConstraints(assignment);

		HistoricItSystemAssignment record = HistoricItSystemAssignment.builder()
			.validFrom(LocalDateTime.now())
			.validTo(null)
			.itSystemId(itSystem.getId())
			.itSystemName(itSystem.getName())
			.itSystemAttestationExempt(itSystem.isAttestationExempt())
			.responsibleUserUuid(resolveResponsibleUserUuid(userRole, itSystem))
			.userRoleId(userRole.getId())
			.userRoleName(userRole.getName())
			.userRoleDescription(userRole.getDescription())
			.systemRoleId(assignment.getSystemRole().getId())
			.systemRoleName(assignment.getSystemRole().getName())
			.systemRoleDescription(assignment.getSystemRole().getDescription())
			.constraints(constraints)
			.build();

		constraints.forEach(c -> c.setHistoricItSystemAssignment(record));
		record.setRecordHash(computeHash(record));
		return record;
	}

	private static List<HistoricItSystemAssignmentConstraint> buildConstraints(SystemRoleAssignment assignment) {
		if (assignment.getConstraintValues() == null) {
			return new ArrayList<>();
		}
		return assignment.getConstraintValues().stream()
			.map(cv -> HistoricItSystemAssignmentConstraint.builder()
				.constraintName(cv.getConstraintType().getName())
				.constraintValueType(cv.getConstraintValueType())
				.constraintValue(cv.getConstraintValue())
				.build())
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	private static String resolveResponsibleUserUuid(UserRole userRole, ItSystem itSystem) {
		return userRole.isRoleAssignmentAttestationByAttestationResponsible()
				&& itSystem.getAttestationResponsible() != null
			? itSystem.getAttestationResponsible().getUuid()
			: null;
	}

	private static String computeHash(HistoricItSystemAssignment r) {
		return dk.digitalidentity.rc.util.HashUtil.builder()
			.add(r.getItSystemId())
			.add(r.getUserRoleId())
			.add(r.getSystemRoleId())
			.add(r.getConstraints().stream()
				.map(c -> c.getConstraintName() + ":" + c.getConstraintValueType() + ":" + c.getConstraintValue())
				.sorted()
				.collect(java.util.stream.Collectors.joining("|")))
			.build();
	}
}
