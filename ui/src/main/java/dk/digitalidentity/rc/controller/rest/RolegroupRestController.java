package dk.digitalidentity.rc.controller.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupView;
import dk.digitalidentity.rc.controller.rest.model.OUFilterDTO;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.service.RoleGroupViewService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupDeleteStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupForm;
import dk.digitalidentity.rc.controller.validator.RolegroupValidator;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.ROLE_GROUP, permission = Permission.READ)
@RestController
public class RolegroupRestController {
	private final UserService userService;
	private final UserRoleService userRoleService;
	private final OrgUnitService orgUnitService;
	private final RoleGroupService roleGroupService;
	private final RolegroupValidator rolegroupValidator;
	private final RoleGroupViewService roleGroupViewService;
	private final AssignmentService assignmentService;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		if (binder.getTarget() == null) {
			return;
		}
		if (rolegroupValidator.supports(binder.getTarget().getClass())) {
			binder.addValidators(rolegroupValidator);
		}
	}

	// have to check against deprecated method to ensure
	@GetMapping(value = "/rest/rolegroups/trydelete/{id}")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.DELETE)
	public RoleGroupDeleteStatus tryDelete(@PathVariable("id") long id) {
		RoleGroupDeleteStatus status = new RoleGroupDeleteStatus();

		RoleGroup roleGroup = roleGroupService.getById(id);
		if (roleGroup == null) {
			status.setSuccess(false);
			return status;
		}

		status.setSuccess(true);

		long count = orgUnitService.countAllWithRoleGroup(roleGroup);
		if (count > 0) {
			status.setOus(count);
			status.setSuccess(false);
		}

		count = assignmentService.countAllUsersWithDirectRoleGroup(roleGroup);
		if (count > 0) {
			status.setUsers(count);
			status.setSuccess(false);
		}

		return status;
	}

	@PostMapping(value = "/rest/rolegroups/flag/{roleId}/{flag}")
	@ResponseBody
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> setSystemRoleFlag(@PathVariable("roleId") long roleId, @PathVariable("flag") String flag, @RequestParam(name = "active") boolean active) {
		RoleGroup role = roleGroupService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Rollebuket", HttpStatus.BAD_REQUEST);
		}

		switch (flag) {
			case "useronly":
				role.setUserOnly(active);
				roleGroupService.save(role);

				if (active) {
					// need inactive assignments as well
					@SuppressWarnings("deprecation")
					List<OrgUnit> orgUnitsWithRole = orgUnitService.getByRoleGroup(role);

					// if assigned to an OrgUnit already, return a warning (HTTP 400 is not really
					// suitable for this, but there does not seem to be HTTP codes to return warnings)
					if (!orgUnitsWithRole.isEmpty()) {
						return new ResponseEntity<>("Opdateret - bemærk eksisterende enheder har denne rolle tildelt allerede!", HttpStatus.BAD_REQUEST);
					}
				}
				break;
			default:
				return new ResponseEntity<>("Ukendt flag: " + flag, HttpStatus.BAD_REQUEST);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}

	record RequesterChangeRequest(List<RequestableBy> requesterPermission) {}
	@PostMapping(value = "/rest/rolegroups/{roleId}/requester")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> setRequesterPermission(@PathVariable("roleId") long roleId, @RequestBody RequesterChangeRequest requesterChangeRequest, BindingResult bindingResult) {
		RoleGroup role = roleGroupService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		if (requesterChangeRequest.requesterPermission == null ) {
			role.setRequesterPermission(List.of(RequestableBy.INHERIT));
		} else {
			role.setRequesterPermission(requesterChangeRequest.requesterPermission);
		}

		roleGroupService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	record ApproverChangeRequest(List<ApprovableBy> approverPermission) {}
	@PostMapping(value = "/rest/rolegroups/{roleId}/approver")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> setApproverPermission(@PathVariable("roleId") long roleId, @RequestBody ApproverChangeRequest approverChangeRequest, BindingResult bindingResult) {
		RoleGroup role = roleGroupService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		if (approverChangeRequest.approverPermission == null ) {
			role.setApproverPermission(List.of(ApprovableBy.INHERIT));
		} else {
			role.setApproverPermission(approverChangeRequest.approverPermission);
		}

		roleGroupService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

    // we have to use deprecated method to ensure that we update inactive users and assignments
    @PostMapping(value = "/rest/rolegroups/delete/{id}")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.DELETE)
    public ResponseEntity<String> deleteRolegroup(@PathVariable("id") long id) {
        RoleGroup roleGroup = roleGroupService.getById(id);
        if (roleGroup == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

		List<OrgUnit> ous = orgUnitService.getAllWithRoleGroupIncludingInactive(roleGroup);
		for (OrgUnit ou : ous) {
			orgUnitService.removeRoleGroup(ou, roleGroup);
			orgUnitService.save(ou);
		}

		// Deduplicate users by UUID
		Map<String, User> uniqueUsers = new HashMap<>();
		for (User user : assignmentService.getUsersWithRoleGroupDirectlyAssignedIncludingInactive(roleGroup)) {
			uniqueUsers.putIfAbsent(user.getUuid(), user);
		}

		List<User> users = new ArrayList<>(uniqueUsers.values());
		for (User user : users) {
			userService.removeRoleGroup(user, roleGroup);
			userService.save(user);
		}

		roleGroupService.delete(roleGroup);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/rolegroups/edit")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> editRoleGroupAsync(@Valid @ModelAttribute("rolegroup") RoleGroupForm roleGroupForm, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			StringBuilder stringBuilder = new StringBuilder();
			for (ObjectError error : bindingResult.getAllErrors()) {
				if (error.getCode() != null) {
					stringBuilder.append(error.getDefaultMessage());
					stringBuilder.append("<br>");
				}
			}
			return new ResponseEntity<>(stringBuilder.toString(), HttpStatus.BAD_REQUEST);
		}
		RoleGroup roleGroup = roleGroupService.getById(roleGroupForm.getId());

		if (roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		roleGroup.setName(roleGroupForm.getName());
		roleGroup.setDescription(roleGroupForm.getDescription());

		roleGroupService.save(roleGroup);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/rolegroups/addrole/{rolegroupid}/{roleid}")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> addRole(@PathVariable("rolegroupid") long rolegroupId, @PathVariable("roleid") long roleId) {
		RoleGroup rolegroup = roleGroupService.getById(rolegroupId);
		UserRole role = userRoleService.getById(roleId);

		if (rolegroup == null || role == null || role.getItSystem().isReadonly() || role.isReadOnly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		roleGroupService.addUserRole(rolegroup, role);
		roleGroupService.save(rolegroup);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Transactional
	@PostMapping("/rest/rolegroups/ouFilterEnabled")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> editOUFilterEnabled(long id, boolean ouFilterEnabled) {
		if (id <= 0) {
			return new ResponseEntity<>("Ugyldig ID for rollebuket", HttpStatus.BAD_REQUEST);
		}

		RoleGroup role = roleGroupService.getById(id);
		if (role == null) {
			return new ResponseEntity<>("Ukendt rollebuket", HttpStatus.BAD_REQUEST);
		}

		role.setOuFilterEnabled(ouFilterEnabled);
		if (!ouFilterEnabled) {
			role.getOrgUnitFilterOrgUnits().clear();
		}
		roleGroupService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Transactional
	@PostMapping("/rest/rolegroups/oufilter")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> editOUFilter(@RequestBody OUFilterDTO dto) {
		RoleGroup role = roleGroupService.getById(dto.getId());
		if (role == null) {
			return new ResponseEntity<>("Ukendt Rollebuket", HttpStatus.BAD_REQUEST);
		}

		List<OrgUnit> ous = orgUnitService.getByUuidIn(dto.getSelectedOUs());
		role.getOrgUnitFilterOrgUnits().clear();
		role.getOrgUnitFilterOrgUnits().addAll(ous);
		roleGroupService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/rolegroups/removerole/{rolegroupid}/{roleid}")
	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	public ResponseEntity<String> removeRole(@PathVariable("rolegroupid") long rolegroupId, @PathVariable("roleid") long roleId) {
		RoleGroup rolegroup = roleGroupService.getById(rolegroupId);
		UserRole role = userRoleService.getById(roleId);

		if (rolegroup == null || role == null || role.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		roleGroupService.removeUserRole(rolegroup, role);
		roleGroupService.save(rolegroup);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/rolegroups/{rolegroupId}/userroles")
	public DataTablesOutput<UserRoleForRoleGroupView> getUserRoleDatatableForRoleGroup(@RequestBody DataTablesInput input, BindingResult bindingResult, @PathVariable long rolegroupId) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<UserRoleForRoleGroupView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());
			return error;
		}
		roleGroupService.getOptionalById(rolegroupId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "no rolegroup with this id"));
		return roleGroupViewService.findAllForRoleGroup(input, rolegroupId);
	}
}
