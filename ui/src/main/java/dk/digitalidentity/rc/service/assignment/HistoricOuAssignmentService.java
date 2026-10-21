package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoricOuAssignmentService {

	private final HistoricOuAssignmentDao historicOuAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final UserRoleDao userRoleDao;
	private final AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Transactional
	public void recordUserRoleAdded(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		historicOuAssignmentDao.save(fromUserRoleAssignment(ou, assignment));
	}

	@Transactional
	public void recordUserRoleUpdatedClose(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		closeOpen(fromUserRoleAssignment(ou, assignment), LocalDateTime.now());
	}

	@Transactional
	public void recordUserRoleUpdatedSaveNew(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		HistoricOuAssignment historicOuAssignment = fromUserRoleAssignment(ou, assignment);
		historicOuAssignmentDao.save(historicOuAssignment);
	}

	@Transactional
	public void recordUserRoleRemoved(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		closeOpen(fromUserRoleAssignment(ou, assignment), LocalDateTime.now());
	}

	/**
	 * Closes the open row of one specific assignment. The hash carries no assignment id, so an identical
	 * assignment that stays on the OU shares the open row - it is then left open.
	 */
	@Transactional
	public void recordUserRoleAssignmentRemoved(OrgUnit ou, OrgUnitUserRoleAssignment removed) {
		HistoricOuAssignment record = fromUserRoleAssignment(ou, removed);
		boolean sharedWithRemaining = ou.getUserRoleAssignments().stream()
			.filter(a -> a.getId() != removed.getId())
			.filter(a -> a.getUserRole().getId() == removed.getUserRole().getId())
			.anyMatch(a -> fromUserRoleAssignment(ou, a).getRecordHash().equals(record.getRecordHash()));

		if (!sharedWithRemaining) {
			closeOpen(record, LocalDateTime.now());
		}
	}

	/** Role group variant of {@link #recordUserRoleAssignmentRemoved}, one row per role in the group. */
	@Transactional
	public void recordRoleGroupAssignmentRemoved(OrgUnit ou, OrgUnitRoleGroupAssignment removed) {
		Set<String> remainingHashes = ou.getRoleGroupAssignments().stream()
			.filter(a -> a.getId() != removed.getId())
			.filter(a -> a.getRoleGroup().getId() == removed.getRoleGroup().getId())
			.flatMap(a -> fromRoleGroupAssignment(ou, a).stream())
			.map(HistoricOuAssignment::getRecordHash)
			.collect(Collectors.toSet());

		LocalDateTime now = LocalDateTime.now();
		fromRoleGroupAssignment(ou, removed).stream()
			.filter(record -> !remainingHashes.contains(record.getRecordHash()))
			.forEach(record -> closeOpen(record, now));
	}

	@Transactional
	public void recordRoleGroupAdded(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		List<HistoricOuAssignment> records = fromRoleGroupAssignment(ou, assignment);
		historicOuAssignmentDao.saveAll(records);
	}

	@Transactional
	public void recordRoleGroupUpdatedClose(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		LocalDateTime now = LocalDateTime.now();
		fromRoleGroupAssignment(ou, assignment).forEach(record -> closeOpen(record, now));
	}

	@Transactional
	public void recordRoleGroupUpdatedSaveNew(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		historicOuAssignmentDao.saveAll(fromRoleGroupAssignment(ou, assignment));
	}

	@Transactional
	public void recordRoleGroupRemoved(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		LocalDateTime now = LocalDateTime.now();
		fromRoleGroupAssignment(ou, assignment).forEach(record -> closeOpen(record, now));
	}

	@Transactional
	public void recordUserRoleAddedToRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		UserRole userRoleWithItSystem = userRoleDao.findByIdWithItSystem(userRole.getId()).orElse(userRole);
		List<HistoricOuAssignment> records = orgUnitRoleGroupAssignmentDao.findByRoleGroup(roleGroup).stream()
			.map(assignment -> fromRoleGroupAssignmentForRole(assignment.getOrgUnit(), assignment, userRoleWithItSystem))
			.toList();
		historicOuAssignmentDao.saveAll(records);
	}

	@Transactional
	public void recordUserRoleRemovedFromRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		historicOuAssignmentDao.closeAllOpenByRoleGroupIdAndRoleId(roleGroup.getId(), userRole.getId(), LocalDateTime.now());
	}

	/**
	 * Recomputes record_hash on one open row, so a row written under an older hash formula can be
	 * closed again. Only writes when the hash actually drifted.
	 *
	 * @return true when the row was rewritten
	 */
	@Transactional
	public boolean repairRecordHash(long id) {
		HistoricOuAssignment record = historicOuAssignmentDao.findWithExclusionsById(id).orElse(null);
		if (record == null || record.getValidTo() != null) {
			return false;
		}

		String recordHash = computeHash(record);
		if (recordHash.equals(record.getRecordHash())) {
			return false;
		}

		stampOrCloseAsDuplicate(record, recordHash);
		return true;
	}

	/** The open historic row this assignment should have right now. */
	public HistoricOuAssignment toHistoricRecord(OrgUnitUserRoleAssignment assignment) {
		return fromUserRoleAssignment(assignment.getOrgUnit(), assignment);
	}

	/** The open historic rows, one per role, this assignment should have right now. */
	public List<HistoricOuAssignment> toHistoricRecords(OrgUnitRoleGroupAssignment assignment) {
		return fromRoleGroupAssignment(assignment.getOrgUnit(), assignment);
	}

	private void closeOpen(HistoricOuAssignment record, LocalDateTime now) {
		if (historicOuAssignmentDao.closeOpenByRecordHashIn(List.of(record.getRecordHash()), now) > 0) {
			return;
		}

		List<String> olderHashes = HistoricOuAssignmentHashCalculator.olderHashes(record);
		if (!olderHashes.isEmpty()) {
			historicOuAssignmentDao.closeOpenByRecordHashIn(olderHashes, now);
		}
	}

	/** Gives an open row a new hash, or closes it when another open row already has that hash. */
	public void stampOrCloseAsDuplicate(HistoricOuAssignment record, String recordHash) {
		if (historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(recordHash)) {
			// Another open row already holds the repaired hash - stamping this one too would leave two
			// open rows sharing a hash, and closeOpen would close both. Close this one as
			// the duplicate instead and leave its old hash, so future events hit the other row.
			record.setValidTo(LocalDateTime.now());
		}
		else {
			record.setRecordHash(recordHash);
		}

		historicOuAssignmentDao.save(record);
	}

	@Transactional
	public boolean seedHistoricRowsFromOrgUnitUserRoleAssignmentId(long id) {
		OrgUnitUserRoleAssignment assignment = orgUnitUserRoleAssignmentDao.findById(id).orElse(null);
		if (assignment == null || assignment.getUserRole() == null || assignment.getUserRole().getItSystem() == null) {
			return false;
		}
		HistoricOuAssignment record = fromUserRoleAssignment(assignment.getOrgUnit(), assignment);
		if (historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(record.getRecordHash())) {
			return true;
		}
		historicOuAssignmentDao.save(record);
		return true;
	}

	@Transactional
	public boolean seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(long id) {
		OrgUnitRoleGroupAssignment assignment = orgUnitRoleGroupAssignmentDao.findById(id).orElse(null);
		if (assignment == null || assignment.getRoleGroup() == null) {
			return false;
		}
		List<HistoricOuAssignment> records = fromRoleGroupAssignment(assignment.getOrgUnit(), assignment);
		for (HistoricOuAssignment record : records) {
			if (historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(record.getRecordHash())) {
				continue;
			}
			historicOuAssignmentDao.save(record);
		}
		return true;
	}

	private HistoricOuAssignment fromUserRoleAssignment(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		UserRole role = assignment.getUserRole();
		ItSystem itSystem = role.getItSystem();

		Long responsibleCollectionId = role.isRoleAssignmentAttestationByAttestationResponsible()
			? attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId()).map(c -> c.getId()).orElse(null)
			: null;

		LocalDateTime assignedWhen = assignment.getAssignedTimestamp() != null
			? assignment.getAssignedTimestamp().toInstant()
				.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
			: LocalDateTime.now();

		List<HistoricOuAssignmentExclusion> exclusions = buildExclusions(assignment);

		HistoricOuAssignment historicOuAssignment = HistoricOuAssignment.builder()
			.validFrom(LocalDateTime.now())
			.validTo(null)
			.ouUuid(ou.getUuid())
			.ouName(ou.getName())
			.itSystemId(itSystem.getId())
			.itSystemName(itSystem.getName())
			.roleId(role.getId())
			.roleName(role.getName())
			.roleDescription(role.getDescription())
			.roleRoleGroupId(null)
			.roleRoleGroupName(null)
			.roleGroupDescription(null)
			.sensitiveRole(role.isSensitiveRole())
			.extraSensitiveRole(role.isExtraSensitiveRole())
			.responsibleCollectionId(responsibleCollectionId)
			.itSystemAttestationExempt(itSystem.isAttestationExempt())
			.assignedThroughType(AssignedThrough.ORGUNIT)
			.assignedThroughUuid(ou.getUuid())
			.assignedThroughName(ou.getName())
			.assignedWhen(assignedWhen)
			.assignedByUserId(assignment.getAssignedByUserId())
			.assignedByName(assignment.getAssignedByName())
			.startDate(assignment.getStartDate())
			.stopDate(assignment.getStopDate())
			.appliesOnlyToManager(assignment.isManager())
			.appliesAlsoToSubstitutes(assignment.isSubstitutes())
			.inheritToChildren(assignment.isInherit())
			.exclusions(exclusions)
			.build();

		exclusions.forEach(e -> e.setHistoricOuAssignment(historicOuAssignment));
		historicOuAssignment.setRecordHash(computeHash(historicOuAssignment));
		return historicOuAssignment;
	}

	private List<HistoricOuAssignment> fromRoleGroupAssignment(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		RoleGroup roleGroup = assignment.getRoleGroup();
		List<HistoricOuAssignmentExclusion> sharedExclusions = buildExclusionsFromRoleGroup(assignment);

		LocalDateTime assignedWhen = assignment.getAssignedTimestamp() != null
			? assignment.getAssignedTimestamp().toInstant()
				.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
			: LocalDateTime.now();

		return roleGroup.getUserRoleAssignments().stream()
			.map(ura -> {
				UserRole role = ura.getUserRole();
				ItSystem itSystem = role.getItSystem();
				List<HistoricOuAssignmentExclusion> exclusions = copyExclusions(sharedExclusions);

				HistoricOuAssignment historicOuAssignment = HistoricOuAssignment.builder()
					.validFrom(LocalDateTime.now())
					.validTo(null)
					.ouUuid(ou.getUuid())
					.ouName(ou.getName())
					.itSystemId(itSystem.getId())
					.itSystemName(itSystem.getName())
					.roleId(role.getId())
					.roleName(role.getName())
					.roleDescription(role.getDescription())
					.roleRoleGroupId(roleGroup.getId())
					.roleRoleGroupName(roleGroup.getName())
					.roleGroupDescription(roleGroup.getDescription())
					.sensitiveRole(role.isSensitiveRole())
					.extraSensitiveRole(role.isExtraSensitiveRole())
					.responsibleCollectionId(null)
					.itSystemAttestationExempt(itSystem.isAttestationExempt())
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.assignedThroughUuid(ou.getUuid())
					.assignedThroughName(ou.getName())
					.assignedWhen(assignedWhen)
					.assignedByUserId(assignment.getAssignedByUserId())
					.assignedByName(assignment.getAssignedByName())
					.startDate(assignment.getStartDate())
					.stopDate(assignment.getStopDate())
					.appliesOnlyToManager(assignment.isManager())
					.appliesAlsoToSubstitutes(assignment.isSubstitutes())
					.inheritToChildren(assignment.isInherit())
					.exclusions(exclusions)
					.build();

				exclusions.forEach(e -> e.setHistoricOuAssignment(historicOuAssignment));
				historicOuAssignment.setRecordHash(computeHash(historicOuAssignment));
				return historicOuAssignment;
			})
			.toList();
	}

	private HistoricOuAssignment fromRoleGroupAssignmentForRole(OrgUnit ou, OrgUnitRoleGroupAssignment assignment, UserRole role) {
		RoleGroup roleGroup = assignment.getRoleGroup();
		ItSystem itSystem = role.getItSystem();
		List<HistoricOuAssignmentExclusion> exclusions = buildExclusionsFromRoleGroup(assignment);

		LocalDateTime assignedWhen = assignment.getAssignedTimestamp() != null
			? assignment.getAssignedTimestamp().toInstant()
				.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
			: LocalDateTime.now();

		HistoricOuAssignment historicOuAssignment = HistoricOuAssignment.builder()
			.validFrom(LocalDateTime.now())
			.validTo(null)
			.ouUuid(ou.getUuid())
			.ouName(ou.getName())
			.itSystemId(itSystem.getId())
			.itSystemName(itSystem.getName())
			.roleId(role.getId())
			.roleName(role.getName())
			.roleDescription(role.getDescription())
			.roleRoleGroupId(roleGroup.getId())
			.roleRoleGroupName(roleGroup.getName())
			.roleGroupDescription(roleGroup.getDescription())
			.sensitiveRole(role.isSensitiveRole())
			.extraSensitiveRole(role.isExtraSensitiveRole())
			.responsibleCollectionId(null)
			.itSystemAttestationExempt(itSystem.isAttestationExempt())
			.assignedThroughType(AssignedThrough.ORGUNIT)
			.assignedThroughUuid(ou.getUuid())
			.assignedThroughName(ou.getName())
			.assignedWhen(assignedWhen)
			.assignedByUserId(assignment.getAssignedByUserId())
			.assignedByName(assignment.getAssignedByName())
			.startDate(assignment.getStartDate())
			.stopDate(assignment.getStopDate())
			.appliesOnlyToManager(assignment.isManager())
			.appliesAlsoToSubstitutes(assignment.isSubstitutes())
			.inheritToChildren(assignment.isInherit())
			.exclusions(exclusions)
			.build();

		exclusions.forEach(e -> e.setHistoricOuAssignment(historicOuAssignment));
		historicOuAssignment.setRecordHash(computeHash(historicOuAssignment));
		return historicOuAssignment;
	}

	private static List<HistoricOuAssignmentExclusion> copyExclusions(List<HistoricOuAssignmentExclusion> source) {
		return source.stream()
			.map(e -> HistoricOuAssignmentExclusion.builder()
				.exclusionType(e.getExclusionType())
				.uuids(e.getUuids())
				.build())
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private static List<HistoricOuAssignmentExclusion> buildExclusions(OrgUnitUserRoleAssignment assignment) {
		List<HistoricOuAssignmentExclusion> exclusions = new ArrayList<>();

		if (assignment.isContainsExceptedUsers() && assignment.getExceptedUsers() != null) {
			List<String> uuids = assignment.getExceptedUsers().stream()
				.map(User::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_USERS)
				.uuids(uuids)
				.build());
		}

		if (assignment.getContainsTitles() == ContainsTitles.POSITIVE && assignment.getTitles() != null) {
			List<String> uuids = assignment.getTitles().stream()
				.map(Title::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.POSITIVE_TITLES)
				.uuids(uuids)
				.build());
		}

		if (assignment.getContainsTitles() == ContainsTitles.NEGATIVE && assignment.getTitles() != null) {
			List<String> uuids = assignment.getTitles().stream()
				.map(Title::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.NEGATIVE_TITLES)
				.uuids(uuids)
				.build());
		}

		if (assignment.isContainsFunctions() && assignment.getFunctions() != null) {
			List<String> uuids = assignment.getFunctions().stream()
				.map(Function::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.FUNCTIONS)
				.uuids(uuids)
				.build());
		}

		if (assignment.isContainsExceptedOus() && assignment.getExceptedOus() != null) {
			List<String> uuids = assignment.getExceptedOus().stream()
				.map(OrgUnit::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_OUS)
				.uuids(uuids)
				.build());
		}

		return exclusions;
	}

	private static List<HistoricOuAssignmentExclusion> buildExclusionsFromRoleGroup(OrgUnitRoleGroupAssignment assignment) {
		List<HistoricOuAssignmentExclusion> exclusions = new ArrayList<>();

		if (assignment.isContainsExceptedUsers() && assignment.getExceptedUsers() != null) {
			List<String> uuids = assignment.getExceptedUsers().stream()
				.map(User::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_USERS)
				.uuids(uuids)
				.build());
		}

		if (assignment.getContainsTitles() == ContainsTitles.POSITIVE && assignment.getTitles() != null) {
			List<String> uuids = assignment.getTitles().stream()
				.map(Title::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.POSITIVE_TITLES)
				.uuids(uuids)
				.build());
		}

		if (assignment.getContainsTitles() == ContainsTitles.NEGATIVE && assignment.getTitles() != null) {
			List<String> uuids = assignment.getTitles().stream()
				.map(Title::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.NEGATIVE_TITLES)
				.uuids(uuids)
				.build());
		}

		if (assignment.isContainsFunctions() && assignment.getFunctions() != null) {
			List<String> uuids = assignment.getFunctions().stream()
				.map(Function::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.FUNCTIONS)
				.uuids(uuids)
				.build());
		}

		if (assignment.isContainsExceptedOus() && assignment.getExceptedOus() != null) {
			List<String> uuids = assignment.getExceptedOus().stream()
				.map(OrgUnit::getUuid)
				.toList();
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_OUS)
				.uuids(uuids)
				.build());
		}

		return exclusions;
	}

	private String computeHash(HistoricOuAssignment r) {
		return HistoricOuAssignmentHashCalculator.compute(r);
	}
}
