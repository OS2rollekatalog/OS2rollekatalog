package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.OrgUnitRoleGroupAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.OrgUnitUserRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.AssignmentScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ExceptedUserScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ExcludedTitleScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.FunctionScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.ManagerScopeAM;
import dk.digitalidentity.rc.controller.api.model.assignmentscopes.TitleScopeAM;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CET_ZONE_ID;

public class AssignmentMapper {

	public static List<OrgUnitUserRoleAssignmentAM> toAM(List<OrgUnitUserRoleAssignment> assignments) {
		if (assignments == null) {
			return null;
		}
		return assignments.stream().map(AssignmentMapper::toAM).toList();
	}

	public static OrgUnitUserRoleAssignmentAM toAM(OrgUnitUserRoleAssignment assignment) {
		return OrgUnitUserRoleAssignmentAM.builder()
			.assignmentId(assignment.getId())
			.assignedAt(toLocalDateTimeFromAssignment(assignment.getAssignedTimestamp()))
			.assignedByName(assignment.getAssignedByName())
			.assignedByUserId(assignment.getAssignedByUserId())
			.assignmentType("USER_ROLE")
			.orgUnit(OrgUnitMapper.toShallowApi(assignment.getOrgUnit()))
			.startDate(assignment.getStartDate())
			.stopDate(assignment.getStopDate())
			.inherit(assignment.isInherit())
			.userRole(RoleMapper.toShallowApi(assignment.getUserRole()))
			.scopes(scopes(assignment))
			.build();
	}

	public static List<OrgUnitRoleGroupAssignmentAM> roleGroupAssignmentsToAM(List<OrgUnitRoleGroupAssignment> assignments) {
		if (assignments == null) {
			return null;
		}
		return assignments.stream().map(AssignmentMapper::toAM).toList();
	}

	public static OrgUnitRoleGroupAssignmentAM toAM(OrgUnitRoleGroupAssignment assignment) {
		return OrgUnitRoleGroupAssignmentAM.builder()
			.assignmentId(assignment.getId())
			.assignedAt(toLocalDateTimeFromAssignment(assignment.getAssignedTimestamp()))
			.assignedByName(assignment.getAssignedByName())
			.assignedByUserId(assignment.getAssignedByUserId())
			.assignmentType("ROLE_GROUP")
			.orgUnit(OrgUnitMapper.toShallowApi(assignment.getOrgUnit()))
			.startDate(assignment.getStartDate())
			.stopDate(assignment.getStopDate())
			.inherit(assignment.isInherit())
			.roleGroup(RoleGroupMapper.toShallowApi(assignment.getRoleGroup()))
			.scopes(scopes(assignment))
			.build();
	}

	private static List<AssignmentScopeAM> scopes(OrgUnitRoleGroupAssignment assignment) {
		//noinspection DuplicatedCode
		if (assignment.getContainsTitles() == ContainsTitles.POSITIVE && assignment.isContainsExceptedUsers()) {
			return List.of(titleScope(assignment.getTitles()), exceptedUserScope(assignment.getExceptedUsers()));
		} else if (assignment.getContainsTitles() == ContainsTitles.POSITIVE) {
			return Collections.singletonList(titleScope(assignment.getTitles()));
		} else if (assignment.getContainsTitles() == ContainsTitles.NEGATIVE) {
			return Collections.singletonList(excludedTitleScope(assignment.getTitles()));
		} else if (assignment.isContainsFunctions()) {
			return Collections.singletonList(functionScope(assignment.getFunctions()));
		} else if (assignment.isManager() || assignment.isSubstitutes()) {
			return Collections.singletonList(managerScope(assignment.isManager(), assignment.isSubstitutes()));
		} else if (assignment.isContainsExceptedUsers()) {
			return Collections.singletonList(exceptedUserScope(assignment.getExceptedUsers()));
		}
		return Collections.emptyList();
	}

	private static List<AssignmentScopeAM> scopes(OrgUnitUserRoleAssignment assignment) {
		//noinspection DuplicatedCode
		if (assignment.getContainsTitles() == ContainsTitles.POSITIVE && assignment.isContainsExceptedUsers()) {
			return List.of(titleScope(assignment.getTitles()), exceptedUserScope(assignment.getExceptedUsers()));
		} else if (assignment.getContainsTitles() == ContainsTitles.POSITIVE) {
			return Collections.singletonList(titleScope(assignment.getTitles()));
		} else if (assignment.getContainsTitles() == ContainsTitles.NEGATIVE) {
			return Collections.singletonList(excludedTitleScope(assignment.getTitles()));
		} else if (assignment.isContainsFunctions()) {
			return Collections.singletonList(functionScope(assignment.getFunctions()));
		} else if (assignment.isManager() || assignment.isSubstitutes()) {
			return Collections.singletonList(managerScope(assignment.isManager(), assignment.isSubstitutes()));
		} else if (assignment.isContainsExceptedUsers()) {
			return Collections.singletonList(exceptedUserScope(assignment.getExceptedUsers()));
		}
		return Collections.emptyList();
	}

	private static ManagerScopeAM managerScope(final boolean manager, final boolean substitute) {
		final var s = new ManagerScopeAM();
		s.setType("MANAGER");
		s.setManager(manager);
		s.setSubstitute(substitute);
		return s;
	}

	private static FunctionScopeAM functionScope(final List<Function> functions) {
		final var s = new FunctionScopeAM();
		s.setType("FUNCTION");
		s.setFunctions(functions.stream()
			.map(Function::getName)
			.collect(Collectors.toSet()));
		return s;
	}

	private static ExcludedTitleScopeAM excludedTitleScope(final List<Title> titles) {
		final var s = new ExcludedTitleScopeAM();
		s.setType("EXCLUDED_TITLE");
		s.setExcludedTitles(
			titles.stream()
				.map(TitleMapper::toShallowApi)
				.toList()
		);
		return s;
	}

	private static ExceptedUserScopeAM exceptedUserScope(final List<User> exceptedUsers) {
		final var s = new ExceptedUserScopeAM();
		s.setType("EXCEPTED_USER");
		s.setExceptedUsers(
			exceptedUsers.stream()
				.map(UserMapper::toShallowApi)
				.collect(Collectors.toSet())
		);
		return s;
	}

	private static TitleScopeAM titleScope(final List<Title> titles) {
		final var s = new TitleScopeAM();
		s.setType("TITLE");
		s.setTitles(
			titles.stream()
				.map(TitleMapper::toShallowApi)
				.collect(Collectors.toSet())
		);
		return s;
	}

	private static LocalDateTime toLocalDateTimeFromAssignment(Date d) {
		if (d == null) {
			return null;
		}
		return d.toInstant().atZone(CET_ZONE_ID).toLocalDateTime();
	}

}
