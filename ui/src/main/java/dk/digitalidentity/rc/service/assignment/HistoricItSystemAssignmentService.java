package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignmentConstraint;
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

	@Transactional
	public void recordSystemRoleAssignmentAdded(UserRole userRole, SystemRoleAssignment assignment) {
		dao.save(toHistoric(userRole, assignment));
	}

	@Transactional
	public void recordSystemRoleAssignmentRemoved(UserRole userRole, SystemRoleAssignment assignment) {
		HistoricItSystemAssignment toClose = toHistoric(userRole, assignment);
		dao.closeOpenByRecordHash(toClose.getRecordHash(), LocalDateTime.now());
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
