package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SelectOUDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentConstraintValueDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleNotAssignedDTO;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireRequesterOrAssignerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RequireControllerPermission(section = Section.USER, permission = Permission.READ)
@Controller
public class UserController {
	private final UserService userService;
	private final UserControllerHelper helper;
	private final KleService kleService;
	private final AccessConstraintService assignerRoleConstraint;
	private final RoleCatalogueConfiguration configuration;
	private final UserRoleService userRoleService;
	private final Select2Service select2Service;
	private final OrgUnitService orgUnitService;
	private final ItSystemService itSystemService;
	private final ManagerSubstituteService managerSubstituteService;
	private final DomainService domainService;
	private final SENumberService seNumberService;
	private final PNumberService pNumberService;
	private final SettingsService settingsService;

	private static final Section permissionEntity = Section.USER;
	private final UserPermissionContext userPermissionContext;

	@Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@GetMapping(value = "/ui/users/list")
	public String view(Model model) {
		model.addAttribute("multipleDomains", domainService.getAll().size() > 1);

		return "users/list";
	}

	@RequirePermission(section = Section.USER, permission = Permission.UPDATE)
	@GetMapping(value = "/ui/users/manage/{uuid}")
	public String manage(Model model, @PathVariable("uuid") String uuid) {
		Optional<User> optionalUser = userService.getOptionalByUuid(uuid);
		if (optionalUser.isEmpty()) {
			return "redirect:../list";
		}
		User user = optionalUser.get();

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint editConstraints = constraintMap.getOrDefault(Permission.UPDATE, new PermissionConstraint(null, null));

		List<OrgUnit> userOrgUnits = orgUnitService.getOrgUnitsForUser(user);

		boolean editable = (SecurityUtil.isUserAssigner() || userPermissionContext.hasPermission(Section.USER, Permission.UPDATE))
				&& userOrgUnits.stream().anyMatch(ou -> editConstraints.allowsOrgunit(ou.getUuid()));

		List<UserRoleNotAssignedDTO> exceptedAssignments = userService.getAllExceptedUserRolesForUser(user);

		model.addAttribute("user", user);
		model.addAttribute("exceptedAssignments", exceptedAssignments);
		model.addAttribute("editable", editable && assignerRoleConstraint.isUserAccessable(user, true));
		model.addAttribute("canAssign", SecurityUtil.isUserAssigner());

		//kle
		List<Kle> kles = kleService.findAll();

		List<KleDTO> kleDTOS = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());
			kleDTO.setText(kle.isActive() ? kle.getCode() + " " + kle.getName() : kle.getCode() + " " + kle.getName() + " [UDGÅET]");
			kleDTOS.add(kleDTO);
		}

		kleDTOS.sort(Comparator.comparing(KleDTO::getText));
		model.addAttribute("allKles", kleDTOS);

		model.addAttribute("kleUiEnabled", configuration.getIntegrations().getKle().isUiEnabled());
		model.addAttribute("canEditKle", SecurityUtil.isUserAssigner()  || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		model.addAttribute("roleAssignmentOrgUnits", orgUnitService.getOrgUnitsForUser(user).stream().map(o -> new SelectOUDTO(o)).collect(Collectors.toList()));
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());

		// TODO: refactor at some point, so we can use the above KLE list instead of this one...
		userService.addPostponedListsToModel(model);

		return "users/manage";
	}

	@RequirePermission(section = Section.USER, permission = Permission.READ)
	@GetMapping(value = "/ui/users/view/{uuid}")
	public String view(Model model, @PathVariable("uuid") String uuid) {
		Optional<User> optionalUser = userService.getOptionalByUuid(uuid);
		if (optionalUser.isEmpty()) {
			return "redirect:../list";
		}
		User user = optionalUser.get();

		List<UserRoleNotAssignedDTO> exceptedAssignments = userService.getAllExceptedUserRolesForUser(user);

		model.addAttribute("user", user);
		model.addAttribute("exceptedAssignments", exceptedAssignments);
		model.addAttribute("editable", false);
		model.addAttribute("canAssign", SecurityUtil.isUserAssigner());

		model.addAttribute("allKles", List.of());

		model.addAttribute("kleUiEnabled", false );
		model.addAttribute("canEditKle", false);
		model.addAttribute("roleAssignmentOrgUnits", orgUnitService.getOrgUnitsForUser(user).stream().map(o -> new SelectOUDTO(o)).collect(Collectors.toList()));
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());

		// TODO: refactor at some point, so we can use the above KLE list instead of this one...
		userService.addPostponedListsToModel(model);

		return "users/manage";
	}

	// since it requires ReadAccess OR manager/subsitute/reqeuster access to get here, if you do not have ReadAccess
	// we can safely assume you must have one of the others - so we scan to see if you have access to this specific user
	private void verifyRequesterOrManagerAccess(User user) {
		if (SecurityUtil.doesNotHaveReadAccess()) {
			final String userId = SecurityUtil.getUserId();

			boolean isManager = user.getPositions().stream()
					.anyMatch(p ->
							// that orgUnits manager is our currently logged in user
							managerSubstituteService.isManagerForOrgUnit(p.getOrgUnit()) ||
							// or our currently logged in user is one of that orgUnits manager's substitutes
							managerSubstituteService.isSubstituteforOrgUnit(p.getOrgUnit())
					 );

			// any of the users positions that points to an OU that has an AuthorizationManager that happens to be our currently logged in user?
			boolean isAuthorizationManager = user.getPositions().stream()
					.anyMatch(p -> p.getOrgUnit().getAuthorizationManagers().stream().anyMatch(a -> Objects.equals(a.getUser().getUserId(), userId)));

			if (!isManager && !isAuthorizationManager) {
				throw new AccessDeniedException("Der er ikke adgang til at se data på denne bruger");
			}
		}
	}

	// TODO - refactoring target - way too big endpoint. Extract service and helper methods
	@Transactional(readOnly = true)
	@RequirePermission(section = Section.USER, permission = Permission.READ)
	@RequestMapping(value = "/ui/users/manage/{uuid}/roles")
	public String getAssignedRolesFragment(Model model, @PathVariable("uuid") String uuid) {
		Optional<User> optionalUser = userService.getOptionalByUuid(uuid);
		if (optionalUser.isEmpty()) {
			return "redirect:../list";
		}
		User user = optionalUser.get();

		PermissionConstraint updateConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE);

		boolean hasEditPermission = userPermissionContext.hasPermission(Section.USER, Permission.UPDATE)	&& updateConstraint.allowsOrgunit(uuid);

		List<RoleAssignedToUserDTO> assignments = userService.getAllUserRoleAndRoleGroupAssignments(user);
		assignments.addAll(userService.getAllNegativeUserRolesAndRoleGroups(user));
		for (RoleAssignedToUserDTO assignment : assignments) {
			boolean directlyAssignedRole = assignment.getAssignedThrough() == AssignedThrough.DIRECT || assignment.getAssignedThrough() == AssignedThrough.POSITION;
			if (assignment.getType() == RoleAssignmentType.USERROLE || assignment.getType() == RoleAssignmentType.NEGATIVE) {
				handleUserRoleOrNegativeRole(assignment, user, assignments, hasEditPermission);
			}
			else if (assignment.getType() == RoleAssignmentType.ROLEGROUP) {
				boolean internalRole = user.getRoleGroupAssignments().stream().filter(rga -> rga.getRoleGroup().getId() == assignment.getRoleId())
						.filter(rga -> rga.getRoleGroup().getUserRoleAssignments() != null)
						.flatMap(rga -> rga.getRoleGroup().getUserRoleAssignments().stream())
						.filter(rg -> rg.getUserRole() != null)
						.anyMatch(rg -> rg.getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER));
				if ((internalRole && SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) && directlyAssignedRole)
						|| (hasEditPermission && directlyAssignedRole)) {
					assignment.setCanEdit(true);
				}
			}
		}

		model.addAttribute("assignments", assignments);
		model.addAttribute("editable", hasEditPermission);
		model.addAttribute("canAssign", SecurityUtil.isUserAssigner());
		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());

		return "users/fragments/manage_roles :: userAssignedRoles";
	}

	private void handleUserRoleOrNegativeRole (RoleAssignedToUserDTO assignment, User user,List<RoleAssignedToUserDTO> assignments, boolean hasEditPermission) {
		final UserRole userRole = userRoleService.getById(assignment.getRoleId());
		boolean internalRole = assignment.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER);
		// We allow editing of internal roles when user is an Administrator (which also allows editing other roles)
		// or if user can edit and role is "directly" assigned
		boolean userRoleEditable = !userRole.isReadOnly()  && hasEditPermission && assignerRoleConstraint.isAssignmentAllowed(user, userRole) && assignment.getAssignedThrough() == AssignedThrough.DIRECT;
		assignment.setCanEdit(userRoleEditable);

		// check if role is ineffective
		if (assignment.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN) && !StringUtils.hasLength(user.getNemloginUuid())) {
			assignment.setIneffectiveReason("NEMLOGIN");
			assignment.setIneffective(true);
		}

		final UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream()
				.filter(ura -> ura.getId() == assignment.getAssignmentId())
//					 Assignments can have same id if they come from different entities, so do further filtering
				.filter(ura -> ura.getUserRole().getId() == assignment.getRoleId())
				.filter(ura -> Objects.equals(ura.getStartDate(), assignment.getStartDate()))
				.findFirst()
				.orElse(null);
		// check system role weights ( only if has 1 systemRole assigned)
		if (userRoleService.isIneffectiveDueToWeight(assignment, assignments)) {
			assignment.setIneffectiveReason("WEIGHT");
			assignment.setIneffective(true);
		}
		assignment.setHighestSystemRoleWeight(userRoleService.highestSystemRolesWeight(userRole));

		boolean kspCicsAccount = user.getAltAccounts().stream().anyMatch(a -> a.getAccountType().equals(AltAccountType.KSPCICS));
		if (!kspCicsAccount && assignment.getItSystem().getSystemType().equals(ItSystemType.KSPCICS)) {
			assignment.setIneffective(true);
		}
		assignment.setDescription(userRole.getDescription());

		// TODO: this block of code is duplicated in multiple places - could it be extracted to a utility method somewhere?
		// it is also found in AttestationController
		if (userUserRoleAssignment != null) {
			List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();
			UserRole role = userUserRoleAssignment.getUserRole();

			if (role.isAllowPostponing()) {
				for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
					List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

					for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
						if (constraintValue.isPostponed()) {
							SystemRoleAssignmentConstraintValueDTO valueDto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);
							PostponedConstraint postponedConstraint = userUserRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

							if (postponedConstraint != null) {
								if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.REGEX)) {
									if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID) ||
											postponedConstraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)) {
										String[] uuids = postponedConstraint.getValue().split(",");
										StringBuilder ouString = new StringBuilder();

										for (String ouUuid : uuids) {
											OrgUnit ou = orgUnitService.getByUuid(ouUuid);
											if (ou != null) {
												if (!ouString.isEmpty()) {
													ouString.append(", ");
												}

												ouString.append(ou.getName());
											}
										}

										valueDto.setConstraintValue(ouString.toString());
									}
									else if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
										String[] ids = postponedConstraint.getValue().split(",");
										StringBuilder itSystemsString = new StringBuilder();

										for (String id : ids) {
											ItSystem itSystem = itSystemService.getById(Integer.parseInt(id));
											if (itSystem != null) {
												if (!itSystemsString.isEmpty()) {
													itSystemsString.append(", ");
												}

												itSystemsString.append(itSystem.getName());
											}
										}

										valueDto.setConstraintValue(itSystemsString.toString());
									}
									else {
										valueDto.setConstraintValue(postponedConstraint.getValue());
									}
								}
								else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_SINGLE)) {
									ConstraintTypeValueSet valueSet = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> v.getConstraintKey().equals(postponedConstraint.getValue())).findAny().orElse(null);
									valueDto.setConstraintValue(valueSet == null ? "" : valueSet.getConstraintValue());
								}
								else if (postponedConstraint.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_MULTI)) {
									String[] keysArr = postponedConstraint.getValue().split(",");
									List<String> keys = Arrays.asList(keysArr);
									List<ConstraintTypeValueSet> valueSets = postponedConstraint.getConstraintType().getValueSet().stream().filter(v -> keys.contains(v.getConstraintKey())).collect(Collectors.toList());
									StringBuilder valuesString = new StringBuilder();

									for (ConstraintTypeValueSet valueSet : valueSets) {
										if (!valuesString.isEmpty()) {
											valuesString.append(", ");
										}

										valuesString.append(valueSet.getConstraintValue());
									}

									valueDto.setConstraintValue(valuesString.toString());
								}
							}

							postponedConstraintValues.add(valueDto);
						}
					}

					if (!postponedConstraintValues.isEmpty()) {
						SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
						systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
						systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);

						systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
					}
				}
			}
			assignment.setSystemRoleAssignments(systemRoleAssignmentsDTOs);
		}
	}

	@RequireAssignerRole
	@RequestMapping(value = "/ui/users/manage/{uuid}/addUserRole")
	public String getAddUserRoleFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		return "users/fragments/manage_add_userrole :: addUserRole";
	}

	@RequireAssignerRole
	@RequestMapping(value = "/ui/users/manage/{uuid}/addRoleGroup")
	public String getAddRoleGroupFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		List<AvailableRoleGroupDTO> roleGroups = helper.getAvailableRoleGroups(user);

		model.addAttribute("roleGroups", roleGroups);
		return "users/fragments/manage_add_rolegroup :: addRoleGroup";
	}

	@RequireRequesterOrAssignerRole
	@RequestMapping(value = "/ui/users/manage/postponedconstraints/{roleId}")
	public String getPostponedConstraintsFragment(Model model, @PathVariable("roleId") long roleId) {
		List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();

		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			log.warn("Attempting to get a fragment for a role that does not exist: " + roleId);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		if (role.isAllowPostponing()) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
				List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();
				for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
					if (constraintValue.isPostponed()) {
						postponedConstraintValues.add(new SystemRoleAssignmentConstraintValueDTO(constraintValue));
					}
				}

				if (!postponedConstraintValues.isEmpty()) {
					SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
					systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
					systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);

					systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
				}
			}
		}

		model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
		model.addAttribute("postponingAllowed", role.isAllowPostponing());
		model.addAttribute("itSystemList", select2Service.getItSystemList());

		if (role.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}

		return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
	}

	@RequireAssignerRole
	@RequestMapping(value = "/ui/users/manage/{userUuid}/postponedconstraints/edit/{assignmentId}")
	public String getPostponedConstraintsEditFragment(Model model, @PathVariable("userUuid") String userUuid, @PathVariable("assignmentId") long assignmentId) {
		List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();

		User user = userService.getByUuid(userUuid);
		if (user == null) {
			log.warn("Attempting to get fragment for user that does not exist: " + userUuid);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		UserUserRoleAssignment userRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == assignmentId).findAny().orElse(null);
		if (userRoleAssignment == null) {
			log.warn("Attempting to get fragment for assignment that does not exist: " + assignmentId);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		UserRole role = userRoleAssignment.getUserRole();
		if (role.isAllowPostponing()) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
				List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

				for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
					if (constraintValue.isPostponed()) {
						SystemRoleAssignmentConstraintValueDTO dto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);
						PostponedConstraint postponedConstraint = userRoleAssignment.getPostponedConstraints().stream().filter(p -> p.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId() && p.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid())).findAny().orElse(null);

						if (postponedConstraint != null) {
							dto.setConstraintValue(postponedConstraint.getValue());
						}

						postponedConstraintValues.add(dto);
					}
				}

				if (!postponedConstraintValues.isEmpty()) {
					SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
					systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
					systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);

					systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
				}
			}
		}

		model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
		model.addAttribute("postponingAllowed", role.isAllowPostponing());
		model.addAttribute("itSystemList", select2Service.getItSystemList());

		if (role.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}

		return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
	}

	@RequestMapping(value = "/ui/users/manage/{uuid}/kle/{type}")
	public String getKleFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("type") String type) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		switch (type) {
		case "PERFORMING":
			model.addAttribute("kles", kleService.getKleAssignments(user, KleType.PERFORMING, true));
			break;
		case "INTEREST":
			model.addAttribute("kles", kleService.getKleAssignments(user, KleType.INTEREST, true));
			break;

		default:
			return "redirect:../list";
		}

		return "fragments/manage_kle :: kle";
	}

	@RequestMapping(value = "/ui/users/manage/{uuid}/kleEdit/{type}")
	public String getKleEditFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("type") String type) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		switch (type) {
		case "PERFORMING":
			model.addAttribute("selectedKles", user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.PERFORMING)).map(UserKLEMapping::getCode).collect(Collectors.toList()));
			break;
		case "INTEREST":
			model.addAttribute("selectedKles", user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.INTEREST)).map(UserKLEMapping::getCode).collect(Collectors.toList()));
			break;

		default:
			return "redirect:../list";
		}

		return "fragments/manage_kle_edit :: kleEdit";
	}
}
