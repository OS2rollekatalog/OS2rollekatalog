package dk.digitalidentity.rc.controller.rest;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
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
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleDeleteStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleForm;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.controller.rest.model.OUFilterDTO;
import dk.digitalidentity.rc.controller.rest.model.UserRoleViewDTO;
import dk.digitalidentity.rc.controller.validator.UserRoleValidator;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.log.AuditLogArgument;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.ADGroupMappingService;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserRoleSelect2DTO;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@RequireControllerPermission(section = Section.USER_ROLE, permission = Permission.READ)
@RequiredArgsConstructor
@Slf4j
@RestController
public class UserRoleRestController {
    private final UserService userService;
    private final OrgUnitService orgUnitService;
    private final UserRoleService userRoleService;
    private final RoleGroupService roleGroupService;
    private final UserRoleValidator userRoleValidator;
    private final SystemRoleService systemRoleService;
    private final ConstraintTypeService constraintTypeService;
    private final RoleCatalogueConfiguration configuration;
    private final UserRoleViewDao userRoleViewDao;
	private final Select2Service select2Service;
	private final ADGroupMappingService adGroupMappingService;
	private final UserPermissionContext userPermissionContext;
	private final AssignmentService assignmentService;

	@InitBinder(value = { "role" })
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(userRoleValidator);
    }

	private static final Section permissionEntity = Section.USER_ROLE;

	@PostMapping("/rest/userroles/list")
	public DataTablesOutput<UserRoleViewDTO> list(@Valid @RequestBody DataTablesInput input, Principal principal) throws Exception {
		DataTablesOutput<UserRoleView> viewOutput;

		Set<Long> constrainedITSystems = userPermissionContext.getConstraint(permissionEntity, Permission.READ).getConstrainedItSystemIds();

		viewOutput = userRoleViewDao.findAll(input, UserRoleService.hasItSystemIdIn(constrainedITSystems));

		Map<Long, List<String>> roleToADGroups = adGroupMappingService.getRoleToADGroupsMap();

		return mapToUserRoleViewDTO(viewOutput, roleToADGroups);
	}

    @PostMapping(value = "/rest/userroles/flag/{roleId}/{flag}")
    @ResponseBody
	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    public ResponseEntity<String> setSystemRoleFlag(@PathVariable("roleId") long roleId, @PathVariable("flag") String flag, @RequestParam(name = "active") boolean active) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
        }

        switch (flag) {
        	case "useronly":
        		role.setUserOnly(active);
        		userRoleService.save(role);

        		if (active) {
        			// also need inactive assignments for this
        			@SuppressWarnings("deprecation")
					List<OrgUnit> orgUnitsWithRole = orgUnitService.getByUserRole(role);

        			// if assigned to an OrgUnit already, return a warning (HTTP 400 is not really
        			// suitable for this, but there does not seem to be HTTP codes to return warnings)
        			if (!orgUnitsWithRole.isEmpty()) {
        	            return new ResponseEntity<>("Opdateret - bemærk eksisterende enheder har denne rolle tildelt allerede!", HttpStatus.BAD_REQUEST);
        			}
        		}
        		break;
        	case "sensitive":
        		role.setSensitiveRole(active);
        		userRoleService.save(role);
        		break;
            case "extra-sensitive":
                role.setExtraSensitiveRole(active);
                userRoleService.save(role);
                break;
            case "attestationByAttestationResponsible":
                role.setRoleAssignmentAttestationByAttestationResponsible(active);
                userRoleService.save(role);
                break;
        	default:
        		return new ResponseEntity<>("Ukendt flag: " + flag, HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

	record RequesterChangeRequest(List<RequestableBy> requesterPermission) {}
	@PostMapping(value = "/rest/userroles/{roleId}/requester")
	@ResponseBody
	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
	public ResponseEntity<String> setRequesterPermission(@PathVariable("roleId") long roleId, @RequestBody RequesterChangeRequest requesterChangeRequest) {
		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		if (requesterChangeRequest.requesterPermission == null || requesterChangeRequest.requesterPermission.isEmpty()) {
			role.setRequesterPermission(List.of(RequestableBy.INHERIT));
		} else {
			role.setRequesterPermission(requesterChangeRequest.requesterPermission);
		}

		userRoleService.save(role);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	record ApproverChangeRequest(List<ApprovableBy> approverPermission) {}
	@PostMapping(value = "/rest/userroles/{roleId}/approver")
	@ResponseBody
	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
	public ResponseEntity<String> setApproverPermission(@PathVariable("roleId") long roleId, @RequestBody ApproverChangeRequest approverChangeRequest) {
		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		if (approverChangeRequest.approverPermission == null ) {
			role.setApproverPermission(List.of(ApprovableBy.INHERIT));
		} else {
			role.setApproverPermission(approverChangeRequest.approverPermission);
		}

		userRoleService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/manageraction/{roleId}/{field}")
    @ResponseBody
    public ResponseEntity<String> setManagerAction(@PathVariable("roleId") long roleId, @PathVariable("field") String field, @RequestParam(name = "checked") boolean checked) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
        }

        switch (field) {
        	case "requireManagerAction":
        		role.setRequireManagerAction(checked);
        		if (!checked) {
        			role.setSendToAuthorizationManagers(false);
        			role.setSendToSubstitutes(false);
        		}
        		break;
        	case "sendToAuthorizationManagers":
        		role.setSendToAuthorizationManagers(checked);
        		break;
        	case "sendToSubstitutes":
        		role.setSendToSubstitutes(checked);
        		break;
        	default:
        		return new ResponseEntity<>("Ukendt flag: " + field, HttpStatus.BAD_REQUEST);
        }

		userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/edit/{roleId}/addSystemRole/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> addSystemRole(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        ItSystem itSystem = role.getItSystem();

        if (itSystem == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);

        //Find and assign system role
        for (SystemRole systemRole : systemRoles) {
            if (systemRole.getId() == systemRoleId) {
                SystemRoleAssignment roleAssignment = new SystemRoleAssignment();
                roleAssignment.setUserRole(role);
                roleAssignment.setSystemRole(systemRole);
                roleAssignment.setAssignedByName(SecurityUtil.getUserFullname());
                roleAssignment.setAssignedByUserId(SecurityUtil.getUserId());
                roleAssignment.setAssignedTimestamp(new Date());

                userRoleService.addSystemRoleAssignment(role, roleAssignment);
                userRoleService.save(role);

                return new ResponseEntity<>(HttpStatus.OK);
            }
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/edit/{roleId}/removeSystemRole/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> removeSystemRole(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        SystemRoleAssignment assignment = null;
    	for (SystemRoleAssignment roleAssignment : role.getSystemRoleAssignments()) {
            if (roleAssignment.getSystemRole().getId() == systemRoleId) {
            	assignment = roleAssignment;
            	break;
            }
		}

        if (assignment != null) {
        	userRoleService.removeSystemRoleAssignment(role, assignment);
            userRoleService.save(role);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/edit/{roleId}/addConstraint/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> addConstraint(@PathVariable("roleId") final long roleId,
                                                @PathVariable("systemRoleId") final long systemRoleId,
                                                final String constraintUuid,
                                                @AuditLogArgument(name = "Værdi") final String constraintValue,
                                                ConstraintValueType constraintValueType,
                                                @AuditLogArgument(name = "Udskudt") final boolean postpone) {
        final UserRole role = userRoleService.getOptionalById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final SystemRole systemRole = systemRoleService.getOptionalById(systemRoleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final SystemRoleAssignment roleAssignment = role.getSystemRoleAssignments().stream().filter(r -> r.getSystemRole().equals(systemRole)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final ConstraintType constraintType = constraintTypeService.getByUuidOptional(constraintUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        constraintValueType = Objects.requireNonNullElse(constraintValueType, ConstraintValueType.VALUE);
        AuditLogContextHolder.getContext().addArgument("Type", constraintType.getName());
        // perform regex validation (if needed)
        if (constraintValueType.equals(ConstraintValueType.VALUE) && constraintType.getRegex() != null && !constraintType.getRegex().isEmpty()) {
        	try {
        		Pattern pattern = Pattern.compile(constraintType.getRegex());
        		Matcher matcher = pattern.matcher(constraintValue);
        		if (!matcher.matches()) {
                    log.warn("Input does not match regular expression: {} for regex: {}", constraintValue, constraintType.getRegex());

        			return new ResponseEntity<>("Ugyldig dataafgrænsningsværdi!", HttpStatus.BAD_REQUEST);
        		}
        	}
        	catch (Exception ex) {
                log.warn("Unable to perform regex validation (giving it a free pass) on '{}'. Message = {}", constraintType.getEntityId(), ex.getMessage());
        	}
        }

        final SystemRoleAssignmentConstraintValue systemRoleAssignmentConstraintValue = new SystemRoleAssignmentConstraintValue();
        systemRoleAssignmentConstraintValue.setConstraintValue(constraintValue);
        systemRoleAssignmentConstraintValue.setSystemRoleAssignment(roleAssignment);
        systemRoleAssignmentConstraintValue.setConstraintType(constraintType);
        systemRoleAssignmentConstraintValue.setConstraintValueType(constraintValueType);
        systemRoleAssignmentConstraintValue.setPostponed(postpone);
        systemRoleAssignmentConstraintValue.setConstraintIdentifier(IdentifierGenerator.buildKombitConstraintIdentifier(
            configuration.getIntegrations().getKombit().getDomain(),
            systemRole,
            roleAssignment,
            constraintType
        ));
        if (userRoleService.findConstraintValue(constraintType, roleAssignment).isPresent()) {
            userRoleService.updateSystemRoleConstraint(roleAssignment, systemRoleAssignmentConstraintValue);
        } else {
            userRoleService.addSystemRoleConstraint(roleAssignment, systemRoleAssignmentConstraintValue);
        }
        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/edit/{roleId}/removeConstraint/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> removeConstraint(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId, String constraintUuid) {
        final UserRole role = userRoleService.getOptionalById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final SystemRole systemRole = systemRoleService.getOptionalById(systemRoleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final SystemRoleAssignment roleAssignment = role.getSystemRoleAssignments().stream().filter(r -> r.getSystemRole().equals(systemRole)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        final ConstraintType constraintType = constraintTypeService.getByUuidOptional(constraintUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        AuditLogContextHolder.getContext().addArgument("Type", constraintType.getName());
        userRoleService.removeSystemRoleConstraint(roleAssignment, constraintType);
        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/edit")
    public ResponseEntity<String> editRoleAsync(@Valid @ModelAttribute("role") UserRoleForm userRoleForm, BindingResult bindingResult, Locale locale) {
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

        UserRole role = userRoleService.getById(userRoleForm.getId());
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

		switch (role.getSystemRoleLinkType()) {
			case NONE:
				role.setName(userRoleForm.getName());
				role.setDescription(userRoleForm.getDescription());
				role.setContactEmail(userRoleForm.getContactEmail());
				break;
			case NAME_ONLY: {
				role.setDescription(userRoleForm.getDescription());
				role.setContactEmail(userRoleForm.getContactEmail());
				break;
			}
			case NAME_AND_DESCRIPTION:
				// do not update
				break;
			default:
				log.error("Unexpected SystemRoleLinkType: " + role.getSystemRoleLinkType());
		}

        if (role.isRequireManagerAction()) {
        	if (role.getUserRoleEmailTemplate() == null) {
        		UserRoleEmailTemplate userRoleEmailTemplate = new UserRoleEmailTemplate();
        		userRoleEmailTemplate.setUserRole(role);

				role.setUserRoleEmailTemplate(userRoleEmailTemplate);
        	}

        	role.getUserRoleEmailTemplate().setTitle(userRoleForm.getEmailTemplateTitle());
        	role.getUserRoleEmailTemplate().setMessage(userRoleForm.getEmailTemplateMessage());
        }

        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }


	@RequirePermission(section = Section.USER_ROLE, permission = Permission.DELETE)
    @PostMapping(value = "/rest/userroles/delete/{id}")
    public ResponseEntity<String> deleteRoleAsync(@PathVariable("id") long id) {
        UserRole role = userRoleService.getById(id);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

		if (role.getItSystem().getSystemType().equals(ItSystemType.KSPCICS) || role.isReadOnly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		// there are not that many rolegroups, we can do a full scan
		for (RoleGroup roleGroup : roleGroupService.getAll()) {
			if (roleGroup.getUserRoleAssignments().stream().map(RoleGroupUserRoleAssignment::getUserRole).toList().contains(role)) {
				roleGroupService.removeUserRole(roleGroup, role);
				roleGroupService.save(roleGroup);
			}
		}

		// Deduplicate users by UUID
		Map<String, User> uniqueUsers = new HashMap<>();
		for (User user : assignmentService.getUsersWithUserRoleDirectlyAssigned(role)) {
			uniqueUsers.putIfAbsent(user.getUuid(), user);
		}

		List<User> users = new ArrayList<>(uniqueUsers.values());
		for (User user : users) {
			userService.removeUserRole(user, role);
			userService.save(user);
		}

		List<OrgUnit> ous = orgUnitService.getAllWithRoleIncludingInactive(role);
		for (OrgUnit orgUnit : ous) {
			orgUnitService.removeUserRole(orgUnit, role);
			orgUnitService.save(orgUnit);
		}

		userRoleService.delete(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.DELETE)
	@GetMapping(value = "/rest/userroles/trydelete/{id}")
    public ResponseEntity<UserRoleDeleteStatus> tryDelete(@PathVariable("id") long id) {
        UserRoleDeleteStatus status = new UserRoleDeleteStatus();

        UserRole userRole = userRoleService.getById(id);
        if (userRole == null) {
        	status.setSuccess(false);
            return new ResponseEntity<>(status, HttpStatus.OK);
        }

        status.setSuccess(true);

        long count = orgUnitService.countAllWithRole(userRole);
        if (count > 0) {
        	status.setOus(count);
        	status.setSuccess(false);
        }

        count = assignmentService.countAllUsersWithDirectUserRole(userRole);
        if (count > 0) {
            status.setUsers(count);
        	status.setSuccess(false);
        }

        // there are not that many role groups, we can do a full scan
        for (RoleGroup roleGroup : roleGroupService.getAll()) {
          	if (roleGroup.getUserRoleAssignments().stream().map(RoleGroupUserRoleAssignment::getUserRole).toList().contains(userRole)) {
                status.setRoleGroups(status.getRoleGroups() + 1);
            	status.setSuccess(false);
            }
        }

        return new ResponseEntity<>(status, HttpStatus.OK);
    }

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/userroles/removeSystemRoleLink/{roleId}")
    @ResponseBody
    public ResponseEntity<String> removeSystemRoleLink(@PathVariable("roleId") long roleId) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
        }

        role.setLinkedSystemRole(null);
        role.setLinkedSystemRolePrefix(null);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);

        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@PostMapping("/rest/userroles/ouFilterEnabled")
	public ResponseEntity<String> editOUFilterEnabled(long id, boolean ouFilterEnabled) {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		role.setOuFilterEnabled(ouFilterEnabled);
		if (!ouFilterEnabled) {
			role.getOrgUnitFilterOrgUnits().clear();
		}
		userRoleService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@ResponseBody
	@PostMapping(value = "/rest/userroles/oufilter")
	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
	public ResponseEntity<String> editOUFilter(@RequestBody OUFilterDTO dto) {
		UserRole role = userRoleService.getById(dto.getId());
		if (role == null) {
			return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
		}

		List<OrgUnit> ous = orgUnitService.getByUuidIn(dto.getSelectedOUs());
		role.getOrgUnitFilterOrgUnits().clear();
		role.getOrgUnitFilterOrgUnits().addAll(ous);
		role = userRoleService.save(role);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	public record Select2Response(List<UserRoleSelect2DTO> results, boolean pagination) {}
	@GetMapping("/rest/userroles/search")
	public Select2Response searchUserRoles(
			@RequestParam(name = "q", required = false) String searchTerm,
			@RequestParam(name = "page", defaultValue = "1") int page,
			@RequestParam(name = "itsystem", required = false, defaultValue = "0") int itSystemId) {

		PageRequest pageable = PageRequest.of(page - 1, 20);

		Page<UserRole> rolesPage;
		if (searchTerm == null || searchTerm.isBlank()) {
			rolesPage = select2Service.findAllSearchableUserroles(pageable);
		} else {
			rolesPage = select2Service.searchUserroles(searchTerm, pageable);
		}

		List<UserRoleSelect2DTO> results = rolesPage.stream()
				.filter(role -> itSystemId == 0 || itSystemId == role.getItSystem().getId())
				.map(role -> new UserRoleSelect2DTO(role.getId(), role.getName(), role.getItSystem().getName()))
				.toList();

		boolean hasMore = rolesPage.hasNext();

		return new Select2Response(results, hasMore);
	}

	public record UserRoleDetailDTO(long id, String name, long itSystemId, String itSystemName) {}
	@GetMapping("/rest/userroles/{id}")
	public ResponseEntity<UserRoleDetailDTO> getUserRoleDetails(@PathVariable Long id) {
		UserRole userRole = userRoleService.getById(id);
		if (userRole == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}

		UserRoleDetailDTO dto = new UserRoleDetailDTO(userRole.getId(), userRole.getName(), userRole.getItSystem().getId(), userRole.getItSystem().getName());

		return ResponseEntity.ok(dto);
	}

	private DataTablesOutput<UserRoleViewDTO> mapToUserRoleViewDTO(
			DataTablesOutput<UserRoleView> viewOutput,
			Map<Long, List<String>> roleToADGroups
	) {
		List<UserRoleViewDTO> dtoList = viewOutput.getData().stream()
				// Map to DTO
				.map(view -> {
					ItemPermissionDTO specificAllowedActions = userRoleService.getSpecificAllowedActionsForUserRoleDTO(view);

					UserRoleViewDTO dto = new UserRoleViewDTO(view);
					dto.setAdGroupNames(roleToADGroups.getOrDefault(view.getId(), new ArrayList<>()));

					dto.setAllowedActions(specificAllowedActions);
					return dto;
				})
				.toList();

		DataTablesOutput<UserRoleViewDTO> dtoOutput = new DataTablesOutput<>();
		dtoOutput.setDraw(viewOutput.getDraw());
		dtoOutput.setRecordsTotal(viewOutput.getRecordsTotal());
		dtoOutput.setRecordsFiltered(viewOutput.getRecordsFiltered());
		dtoOutput.setData(dtoList);
		dtoOutput.setError(viewOutput.getError());
		return dtoOutput;
	}
}
