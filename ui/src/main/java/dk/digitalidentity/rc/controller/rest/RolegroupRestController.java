package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleForRoleGroupViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleForRoleGroupView;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableITSystemDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupDeleteStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupForm;
import dk.digitalidentity.rc.controller.validator.RolegroupValidator;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.List;

@RequireAdministratorRole
@RestController
public class RolegroupRestController {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private PositionService positionService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private RolegroupValidator rolegroupValidator;

	@Autowired
	private UserRoleForRoleGroupViewDao userRoleForRoleGroupViewDao;

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
	@SuppressWarnings("deprecation")
	@GetMapping(value = "/rest/rolegroups/trydelete/{id}")
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

		count = userService.countAllWithRoleGroup(roleGroup);
		if (count > 0) {
			status.setUsers(count);
			status.setSuccess(false);
		}

		// have to fetch all positions, as we need to check against users - perhaps a JOIN in the future ;)
		for (Position position : positionService.getAllWithRoleGroup(roleGroup)) {
			if (!position.getUser().isDeleted()) {
				status.setUsers(status.getUsers() + 1);
				status.setSuccess(false);
			}
		}

		return status;
	}

	@PostMapping(value = "/rest/rolegroups/flag/{roleId}/{flag}")
	@ResponseBody
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
					if (orgUnitsWithRole.size() > 0) {
						return new ResponseEntity<>("Opdateret - bemærk eksisterende enheder har denne rolle tildelt allerede!", HttpStatus.BAD_REQUEST);
					}
				}
				break;
			default:
				return new ResponseEntity<>("Ukendt flag: " + flag, HttpStatus.BAD_REQUEST);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}

	record RequesterChangeRequest(RequesterOption requesterPermission) {}
	@PostMapping(value = "/rest/rolegroups/{roleId}/requester")
	public ResponseEntity<String> setRequesterPermission(@PathVariable("roleId") long roleId, @RequestBody RequesterChangeRequest requesterChangeRequest, BindingResult bindingResult) {
		RoleGroup role = roleGroupService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		if (requesterChangeRequest.requesterPermission == null ) {
			role.setRequesterPermission(RequesterOption.INHERIT);
		} else {
			role.setRequesterPermission(requesterChangeRequest.requesterPermission);
		}

		roleGroupService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	record ApproverChangeRequest(ApproverOption approverPermission) {}
	@PostMapping(value = "/rest/rolegroups/{roleId}/approver")
	public ResponseEntity<String> setApproverPermission(@PathVariable("roleId") long roleId, @RequestBody ApproverChangeRequest approverChangeRequest, BindingResult bindingResult) {
		RoleGroup role = roleGroupService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		if (approverChangeRequest.approverPermission == null ) {
			role.setApproverPermission(ApproverOption.INHERIT);
		} else {
			role.setApproverPermission(approverChangeRequest.approverPermission);
		}

		roleGroupService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

    // we have to use deprecated method to ensure that we update inactive users and assignments
    @SuppressWarnings("deprecation")
    @PostMapping(value = "/rest/rolegroups/delete/{id}")
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

		List<User> users = userService.getByRoleGroupsIncludingInactive(roleGroup);
		for (User user : users) {
			userService.removeRoleGroup(user, roleGroup);
			userService.save(user);
		}

		for (Position position : positionService.getAllWithRoleGroup(roleGroup)) {
			positionService.removeRoleGroup(position, roleGroup);
			positionService.save(position);
		}

		roleGroupService.delete(roleGroup);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/rolegroups/edit")
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
	public ResponseEntity<String> addRole(@PathVariable("rolegroupid") long rolegroupId, @PathVariable("roleid") long roleId) {
		RoleGroup rolegroup = roleGroupService.getById(rolegroupId);
		UserRole role = userRoleService.getById(roleId);

		if (rolegroup == null || role == null || role.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		roleGroupService.addUserRole(rolegroup, role);
		roleGroupService.save(rolegroup);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/rolegroups/removerole/{rolegroupid}/{roleid}")
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
	public DataTablesOutput<UserRoleForRoleGroupView> getUserRoleDatatableForUser(@RequestBody DataTablesInput input, BindingResult bindingResult, @PathVariable long rolegroupId) {

		if (bindingResult.hasErrors()) {
			DataTablesOutput<UserRoleForRoleGroupView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());
			return error;
		}

		RoleGroup rolegroup = roleGroupService.getById(rolegroupId);

		Specification<UserRoleForRoleGroupView> specification = UserRoleForRoleGroupViewDao.hasRoleGroupId(rolegroup.getId());
		return userRoleForRoleGroupViewDao.findAll(input, specification);
	}
}
