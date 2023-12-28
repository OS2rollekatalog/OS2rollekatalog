package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SelectOUDTO;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.SENumberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableUserRoleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
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
import dk.digitalidentity.rc.security.RequireReadAccessOrRequesterOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireReadAccessOrRequesterOrManagerRole
@Controller
public class UserController {

	@Autowired
	private UserService userService;

	@Autowired
	private UserControllerHelper helper;

	@Autowired
	private KleService kleService;

	@Autowired
	private AccessConstraintService assignerRoleConstraint;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private Select2Service select2Service;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private SENumberService seNumberService;

	@Autowired
	private PNumberService pNumberService;
    
    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@GetMapping(value = "/ui/users/list")
	public String view(Model model) {
		model.addAttribute("multipleDomains", domainService.getAll().size() > 1);

		return "users/list";
	}

	@RequireReadAccessOrRequesterOrManagerRole
	@GetMapping(value = "/ui/users/manage/{uuid}")
	public String manage(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:/ui/users/list";
		}

		verifyRequesterOrManagerAccess(user);
		
		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<UserRoleNotAssignedDTO> exceptedAssignments = userService.getAllExceptedUserRolesForUser(user);

		model.addAttribute("user", user);
		model.addAttribute("exceptedAssignments", exceptedAssignments);
		model.addAttribute("editable", !readOnly && assignerRoleConstraint.isUserAccessable(user, true));

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
		model.addAttribute("canEditKle", SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		model.addAttribute("roleAssignmentOrgUnits", orgUnitService.getOrgUnitsForUser(user).stream().map(o -> new SelectOUDTO(o)).collect(Collectors.toList()));
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());

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

	@RequireReadAccessOrRequesterOrManagerRole
	@RequestMapping(value = "/ui/users/manage/{uuid}/roles")
	public String getAssignedRolesFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}
		
		verifyRequesterOrManagerAccess(user);

		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		boolean editable = !readOnly && assignerRoleConstraint.isUserAccessable(user, true);

		List<RoleAssignedToUserDTO> assignments = userService.getAllUserRoleAndRoleGroupAssignments(user);
		for (RoleAssignedToUserDTO assignment : assignments) {
			boolean directlyAssignedRole = assignment.getAssignedThrough() == AssignedThrough.DIRECT || assignment.getAssignedThrough() == AssignedThrough.POSITION;

			if (assignment.getType() == RoleAssignmentType.USERROLE) {
				boolean internalRole = assignment.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER);
				// We allow editing of internal roles when user is an Administrator (which also allows editing other roles)
				// or if user can edit and role is "directly" assigned
				if ((internalRole && SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) && directlyAssignedRole) || (editable && directlyAssignedRole)) {
					assignment.setCanEdit(true);
				}
				
				// check if role is ineffective
				if (assignment.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN) && !StringUtils.hasLength(user.getNemloginUuid())) {
					assignment.setIneffective(true);
				}
				
				boolean kspCicsAccount = user.getAltAccounts().stream().anyMatch(a -> a.getAccountType().equals(AltAccountType.KSPCICS));
				if (!kspCicsAccount && assignment.getItSystem().getSystemType().equals(ItSystemType.KSPCICS)) {
					assignment.setIneffective(true);
				}
				
				// TODO: this block of code is duplicated in multiple places - could it be extracted to a utility method somewhere?
				// it is also found in AttestationController
				UserUserRoleAssignment userUserRoleAssignment = user.getUserRoleAssignments().stream().filter(ura->ura.getId() == assignment.getAssignmentId()).findAny().orElse(null);
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
											if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
												String[] uuids = postponedConstraint.getValue().split(",");
												String ouString = "";

												for (String ouUuid : uuids) {
													OrgUnit ou = orgUnitService.getByUuid(ouUuid);
													if (ou != null) {
														if (ouString.length() > 0) {
															ouString += ", ";
														}
														
														ouString += ou.getName();
													}
												}
												
												valueDto.setConstraintValue(ouString);
											}
											else if (postponedConstraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
												String[] ids = postponedConstraint.getValue().split(",");
												String itSystemsString = "";

												for (String id : ids) {
													ItSystem itSystem = itSystemService.getById(Integer.parseInt(id));
													if (itSystem != null) {
														if (itSystemsString.length() > 0) {
															itSystemsString += ", ";
														}
														
														itSystemsString += itSystem.getName();
													}
												}
												
												valueDto.setConstraintValue(itSystemsString);
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
											String valuesString = "";

											for (ConstraintTypeValueSet valueSet : valueSets) {
												if (valuesString.length() > 0) {
													valuesString += ", ";
												}
												
												valuesString += valueSet.getConstraintValue();
											}
											
											valueDto.setConstraintValue(valuesString);
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
			else if (assignment.getType() == RoleAssignmentType.ROLEGROUP) {
				if (editable && directlyAssignedRole) {
					assignment.setCanEdit(true);
				}
			}
		}

		model.addAttribute("assignments", assignments);
		model.addAttribute("editable", editable);

		return "users/fragments/manage_roles :: userAssignedRoles";
	}

	@RequireAssignerRole
	@RequestMapping(value = "/ui/users/manage/{uuid}/addUserRole")
	public String getAddUserRoleFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		List<AvailableUserRoleDTO> roles = helper.getAvailableUserRoles(user);

		model.addAttribute("roles", roles);
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
	
	@RequireAssignerRole
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
