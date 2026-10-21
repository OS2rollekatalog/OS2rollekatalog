package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.util.Failure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The annotation only establishes that the user is an org unit assigner at all; which
 * org units and it systems they may assign within is checked per request below.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequireControllerPermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
@RequestMapping("/rest/ou/{ouUuid}/{roleType}/{roleId}")
public class OrgUnitAssignmentRestController {

	private final OrgUnitService orgUnitService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final OuAssignmentService ouAssignmentService;
	private final UserPermissionContext userPermissionContext;

	/** 422 rather than 400: the request parsed fine, the assignment just is not acceptable yet. */
	@ExceptionHandler(AssignmentValidationException.class)
	public ResponseEntity<Map<String, List<String>>> handleAssignmentValidation(AssignmentValidationException exception) {
		return ResponseEntity.unprocessableContent().body(Map.of("errors", exception.errors()));
	}

	@PostMapping("assign")
	public ResponseEntity<Void> assign(@PathVariable String ouUuid, @PathVariable RoleType roleType,
			@PathVariable long roleId, @RequestBody OrgUnitAssignmentRequest request) {
		OrgUnit ou = orgUnitService.getOptionalByUuid(ouUuid)
				.orElseThrow(() -> Failure.notFound(log, "Org unit {} was not found", ouUuid));

		log.info("OU assignment requested: actor={}, ouUuid={}, roleType={}, roleId={}, scopeChoice={}, scopeFields={}, startDate={}, stopDate={}, caseNumber={}",
				SecurityUtil.getUserId(), ouUuid, roleType, roleId, request.scopeChoice(), request.scopeFields(),
				request.startDate(), request.stopDate(), request.caseNumber());

		switch (roleType) {
			case USERROLE -> {
				UserRole role = userRoleService.getOptionalById(roleId)
						.orElseThrow(() -> Failure.notFound(log, "Role {} {} was not found", roleType, roleId));
				requireAssignable(ou, roleType, roleId, constraint -> userRoleService.isUserRoleAssignable(role, true, constraint));
				ouAssignmentService.assign(ou, role, request);
			}
			case ROLEGROUP -> {
				RoleGroup roleGroup = roleGroupService.getOptionalById(roleId)
						.orElseThrow(() -> Failure.notFound(log, "Role {} {} was not found", roleType, roleId));
				requireAssignable(ou, roleType, roleId, constraint -> userRoleService.isRoleGroupAssignable(roleGroup, true, constraint));
				ouAssignmentService.assign(ou, roleGroup, request);
			}
		}

		return ResponseEntity.ok().build();
	}

	@PostMapping("assignment/{assignmentId}")
	public ResponseEntity<Void> update(@PathVariable String ouUuid, @PathVariable RoleType roleType,
			@PathVariable long roleId, @PathVariable long assignmentId, @RequestBody OrgUnitAssignmentRequest request) {
		OrgUnit ou = orgUnitService.getOptionalByUuid(ouUuid)
				.orElseThrow(() -> Failure.notFound(log, "Org unit {} was not found", ouUuid));

		log.info("OU assignment update requested: actor={}, ouUuid={}, roleType={}, roleId={}, assignmentId={}, scopeChoice={}, scopeFields={}, startDate={}, stopDate={}, caseNumber={}",
				SecurityUtil.getUserId(), ouUuid, roleType, roleId, assignmentId, request.scopeChoice(), request.scopeFields(),
				request.startDate(), request.stopDate(), request.caseNumber());

		switch (roleType) {
			case USERROLE -> {
				OrgUnitUserRoleAssignment assignment = ouAssignmentService.findUserRoleAssignment(ouUuid, roleId, assignmentId)
						.orElseThrow(() -> Failure.notFound(log, "Assignment {} was not found (ouUuid={}, roleType={}, roleId={})", assignmentId, ouUuid, roleType, roleId));
				UserRole role = assignment.getUserRole();
				requireAssignable(ou, roleType, roleId, constraint -> userRoleService.isUserRoleAssignable(role, true, constraint));
				ouAssignmentService.update(assignment, request);
			}
			case ROLEGROUP -> {
				OrgUnitRoleGroupAssignment assignment = ouAssignmentService.findRoleGroupAssignment(ouUuid, roleId, assignmentId)
						.orElseThrow(() -> Failure.notFound(log, "Assignment {} was not found (ouUuid={}, roleType={}, roleId={})", assignmentId, ouUuid, roleType, roleId));
				RoleGroup roleGroup = assignment.getRoleGroup();
				requireAssignable(ou, roleType, roleId, constraint -> userRoleService.isRoleGroupAssignable(roleGroup, true, constraint));
				ouAssignmentService.update(assignment, request);
			}
		}

		return ResponseEntity.ok().build();
	}

	/**
	 * The it system side is delegated to the role assignability check; the org unit side
	 * is not covered by it and is applied here.
	 */
	private void requireAssignable(OrgUnit ou, RoleType roleType, long roleId, Predicate<PermissionConstraint> itSystemAllowed) {
		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);
		boolean allowed = itSystemAllowed.test(assignConstraint)
				&& (SecurityUtil.hasDirectAdminRole() || assignConstraint.allowsOrgunit(ou.getUuid()));

		if (!allowed) {
			throw Failure.accessDenied(log, "Role {} {} may not be assigned in org unit {} by {}",
					roleType, roleId, ou.getUuid(), SecurityUtil.getUserId());
		}
	}

}
