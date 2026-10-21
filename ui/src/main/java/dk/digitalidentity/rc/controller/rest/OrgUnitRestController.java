package dk.digitalidentity.rc.controller.rest;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.controller.rest.model.BulkRemoveAssignmentRequestDTO;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireKleAdministratorRole;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.attestation.exception.ReportModuleBusyException;
import dk.digitalidentity.rc.service.assignment.OrgUnitDuplicateAssignmentService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RequireControllerPermission(section = Section.ORGUNIT, permission = Permission.READ)
@RestController
public class OrgUnitRestController {
	private final AccessConstraintService accessConstraintService;
	private final OrgUnitService orgUnitService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final KleService kleService;
	private final UserService userService;
	private final AuditLogger auditLogger;
	private final OrgUnitDuplicateAssignmentService orgUnitDuplicateAssignmentService;

	@GetMapping(value = "/rest/ous/getKle/{parentCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<KleViewModel> getKle(@PathVariable("parentCode") String parentCode) {
		return kleService.getKleListFromParent(parentCode);
	}

	@RequireKleAdministratorRole
	@PostMapping(value = "/rest/ous/{uuid}/inherit")
	@ResponseBody
	public HttpEntity<String> setKleInherit(@PathVariable("uuid") String uuid, @RequestParam(name = "active") boolean active) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (active) {
			ou.setInheritKle(true);
			orgUnitService.save(ou);
		}
		else {
			ou.setInheritKle(false);
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireKleAdministratorRole
	@PostMapping(value = "/rest/ous/updateAll/kle")
	@ResponseBody
	public HttpEntity<String> updateKle(@RequestHeader("uuid") String uuid, @RequestHeader("type") String type, @RequestBody List<String> codes) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		KleType kleType;
		switch (type) {
			case "PERFORMING":
			case "KlePrimary":
				kleType = KleType.PERFORMING;
				break;
			case "INTEREST":
			case "KleSecondary":
				kleType = KleType.INTEREST;
				break;
			default:
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Set<String> oldCodes = ou.getKles().stream().filter(kleMapping -> kleMapping.getAssignmentType().equals(kleType)).map(KLEMapping::getCode).collect(Collectors.toSet());
		Set<String> newCodes = new HashSet<>(codes);

		//Find difference
		Set<String> intersect = new HashSet<>(oldCodes);
		intersect.retainAll(newCodes);

		oldCodes.removeAll(intersect);
		newCodes.removeAll(intersect);

		//Add / Remove differences one by one
		for (String code : oldCodes) {
			orgUnitService.removeKLE(ou, kleType, code);
		}

		for (String code : newCodes) {
			orgUnitService.addKLE(ou, kleType, code);
		}
		orgUnitService.save(ou);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.UPDATE)
	@PostMapping(value = "/rest/ous/{uuid}/setLevel/{level}")
	public ResponseEntity<String> setLevel(@PathVariable("uuid") String uuid, @PathVariable("level") OrgUnitLevel level) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>("Ukendt enhed", HttpStatus.BAD_REQUEST);
		}

		List<OrgUnitLevel> allowedLevels = orgUnitService.getAllowedLevels(ou);

		if (!allowedLevels.contains(level)) {
			return new ResponseEntity<>("Tildeling ikke lovlig", HttpStatus.BAD_REQUEST);
		}

		ou.setLevel(level);
		orgUnitService.save(ou);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/ous/{uuid}/removeassignment/{type}/{assignmentId}")
	public ResponseEntity<String> removeUserRoleOrRoleGroupAssignmentFromOrgUnit(@PathVariable("uuid") String uuid, @PathVariable("assignmentId") long assignmentId, @PathVariable("type") RoleAssignmentType type) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (type == RoleAssignmentType.USERROLE) {
			if (!accessConstraintService.isUserRoleAssignmentAllowed(ou, assignmentId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ikke tilladt");
			}

			if (orgUnitService.removeUserRoleAssignment(ou, assignmentId)) {
				orgUnitService.save(ou);
			}
		} else if (type == RoleAssignmentType.ROLEGROUP) {
			if (!accessConstraintService.isUserRoleGroupAssignmentAllowed(ou, assignmentId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ikke tilladt");
			}

			if (orgUnitService.removeRoleGroupAssignment(ou, assignmentId)) {
				orgUnitService.save(ou);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/ous/cleanupDuplicateUserRoleAssignments")
	public ResponseEntity<String> cleanupDuplicateUserRoleAssignments() {
		try {
			orgUnitDuplicateAssignmentService.removeDuplicateUserRoleAssignments();
		}
		catch (ReportModuleBusyException ex) {
			// another clean-up is already running
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/ous/cleanupDuplicateRoleGroupAssignments")
	public ResponseEntity<String> cleanupDuplicateRoleGroupAssignments() {
		try {
			orgUnitDuplicateAssignmentService.removeDuplicateRoleGroupAssignments();
		}
		catch (ReportModuleBusyException ex) {
			// another clean-up is already running
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/ous/{uuid}/bulkremoveassignments")
	@ResponseBody
	public ResponseEntity<?> bulkRemoveAssignmentsFromOrgUnit(@PathVariable("uuid") String uuid, @RequestBody BulkRemoveAssignmentRequestDTO request) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		boolean anyRemoved = false;
		for (BulkRemoveAssignmentRequestDTO.AssignmentToRemoveDTO assignmentToRemove : request.getAssignments()) {
			if (assignmentToRemove.getType() == RoleAssignmentType.USERROLE) {
				if (!accessConstraintService.isUserRoleAssignmentAllowed(ou, assignmentToRemove.getAssignmentId())) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ikke tilladt");
				}

				if (orgUnitService.removeUserRoleAssignment(ou, assignmentToRemove.getAssignmentId())) {
					anyRemoved = true;
				}
			} else if (assignmentToRemove.getType() == RoleAssignmentType.ROLEGROUP) {
				if (!accessConstraintService.isUserRoleGroupAssignmentAllowed(ou, assignmentToRemove.getAssignmentId())) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ikke tilladt");
				}

				if (orgUnitService.removeRoleGroupAssignment(ou, assignmentToRemove.getAssignmentId())) {
					anyRemoved = true;
				}
			}
		}

		if (anyRemoved) {
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/ous/removerole/{uuid}/{roleid}")
	public ResponseEntity<String> removeRoleAsync(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId) {
		// TODO Add access check!!
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (ou == null || role == null) {
			return new ResponseEntity<>("Enheden eller jobfunktionsrollen kunne ikke findes", HttpStatus.BAD_REQUEST);
		}

		Optional<String> blockedReason = userRoleService.assignmentBlockedReason(role);
		if (blockedReason.isPresent()) {
			return new ResponseEntity<>(blockedReason.get(), HttpStatus.BAD_REQUEST);
		}

		if (ou.getUserRoleAssignments().stream().anyMatch(a -> a.getUserRole().getId() == role.getId())) {
			if (orgUnitService.removeUserRole(ou, role)) {
				orgUnitService.save(ou);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@PostMapping(value = "/rest/ous/removerolegroup/{uuid}/{rolegroupid}")
	public ResponseEntity<String> removeRoleGroup(@PathVariable("uuid") String uuid, @PathVariable("rolegroupid") long roleGroupId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

		if (ou == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (!accessConstraintService.isAssignmentAllowed(ou, roleGroup)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ikke tilladt");
		}

		if (ou.getRoleGroupAssignments().stream().anyMatch(a -> a.getRoleGroup().getId() == roleGroup.getId())) {
			if (orgUnitService.removeRoleGroup(ou, roleGroup)) {
				orgUnitService.save(ou);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.UPDATE)
	@PostMapping("/rest/ous/{uuid}/authorizationmanager/save")
	@ResponseBody
	public ResponseEntity<HttpStatus> saveAuthManager(@RequestBody String personUuid, @PathVariable String uuid) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to find orgUnit with uuid = " + uuid);
			return ResponseEntity.badRequest().build();
		}

		User user = userService.getByUuid(personUuid);
		if (user == null) {
			log.warn("Unable to find user with uuid = " + personUuid);
			return ResponseEntity.badRequest().build();
		}

		boolean found = false;
		for (AuthorizationManager authManager : orgUnit.getAuthorizationManagers()) {
			if (Objects.equals(authManager.getUser().getUuid(), user.getUuid())) {
				found = true;
				break;
			}
		}

		if (!found) {
			AuthorizationManager manager = new AuthorizationManager();
			manager.setOrgUnit(orgUnit);
			manager.setUser(user);
			orgUnit.getAuthorizationManagers().add(manager);
			orgUnitService.save(orgUnit);

			auditLogger.log(orgUnit, EventType.AUTH_MANAGER_ADDED, user);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.UPDATE)
	@PostMapping("/rest/ous/{uuid}/authorizationmanager/remove")
	@ResponseBody
	public ResponseEntity<HttpStatus> removeAuthManager(@RequestBody String personUuid, @PathVariable String uuid) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			return ResponseEntity.badRequest().build();
		}

		User user = userService.getByUuid(personUuid);
		if (user == null) {
			return ResponseEntity.badRequest().build();
		}

		for (Iterator<AuthorizationManager> iterator = orgUnit.getAuthorizationManagers().iterator(); iterator.hasNext();) {
			AuthorizationManager authManager = iterator.next();

			if (Objects.equals(authManager.getUser().getUuid(), personUuid)) {
				iterator.remove();

				auditLogger.log(orgUnit, EventType.AUTH_MANAGER_REMOVED, user);
				break;
			}
		}

		orgUnitService.save(orgUnit);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@Transactional(readOnly = true)
	@GetMapping("/rest/ous/{uuid}/childtree")
	public ResponseEntity<List<OrgUnitService.OuTreeNode>> getChildTree(@PathVariable("uuid") String uuid) {
		return orgUnitService.findDescendantTree(uuid)
				.map(nodes -> new ResponseEntity<>(nodes, HttpStatus.OK))
				.orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
	}

}
