package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ConvertSystemRolesForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ItSystemForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleViewModel;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.controller.validator.ItSystemValidator;
import dk.digitalidentity.rc.controller.validator.SystemRoleValidator;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemMaster;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.ADGroupMappingService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemMasterService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@RequireControllerPermission(section = Section.IT_SYSTEM, permission = Permission.READ)
@Controller
public class ItSystemController {
	private final ItSystemService itSystemService;
	private final SystemRoleService systemRoleService;
	private final ItSystemValidator itSystemValidator;
	private final SystemRoleValidator systemRoleValidator;
	private final ItSystemMasterService itSystemMasterService;
	private final UserRoleService userRoleService;
	private final PendingADUpdateService pendingADUpdateService;
	private final OrgUnitService orgUnitService;
	private final DomainService domainService;
	private final UserService userService;
	private final SettingsService settingsService;
	private final Select2Service select2Service;
	private final RoleCatalogueConfiguration configuration;
	private final ADGroupMappingService adGroupMappingService;

	private static final Section permissionEntity = Section.IT_SYSTEM;
	private final UserPermissionContext userPermissionContext;

	@InitBinder(value = { "itSystemForm" })
	public void initBinderItSystemForm(WebDataBinder binder) {
		binder.addValidators(itSystemValidator);
	}

	@InitBinder(value = { "systemRoleForm" })
	public void initBinderSystemRoleForm(WebDataBinder binder) {
		binder.addValidators(systemRoleValidator);
	}

	record ITSystemListDTO(long id, String name, boolean hidden, String identifier, ItSystemType systemType, boolean accessBlocked, boolean paused, List<String> adSyncServiceLabels, Set<RequestableBy> requestPermission, Set<ApprovableBy> approvePermission, ItemPermissionDTO allowedActions) {}
	@GetMapping(value = { "/ui/itsystem", "/ui/itsystem/list" })
	public String list(Model model) {
		List<ItSystem> itSystems = new ArrayList<>();

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraints = constraintMap.getOrDefault(Permission.READ, new PermissionConstraint(null, null));

		if (readConstraints.getConstrainedItSystemIds() == null) {
			itSystems = itSystemService.getAllByDeletedFalse();
		} else if(!readConstraints.getConstrainedItSystemIds().isEmpty()) {
			itSystems = itSystemService.getAllByIdInAndDeletedFalse(readConstraints.getConstrainedItSystemIds());
		}

		ItemPermissionDTO allowedActions = userPermissionContext.getAllowedActionsForSection(permissionEntity);

		List<ApprovableBy> globalApprovables = settingsService.getRolerequestApprover();
		List<RequestableBy> globalRequestables = settingsService.getRolerequestRequester();
		Map<Long, List<String>> mappingsForLabel = adGroupMappingService.getItSystemToADGroupsMap();
		List<ITSystemListDTO> listDTOS = itSystems.stream()
				.map(i -> {
					ItSystemType systemType = i.getSystemType();
					boolean canCreate = allowedActions.isDuplicateable()
							&& constraintMap.getOrDefault(Permission.CREATE, new PermissionConstraint(null, null)).allowsITSystem(i.getId());
					boolean canRead = allowedActions.isReadable()
							&& constraintMap.getOrDefault(Permission.READ, new PermissionConstraint(null, null)).allowsITSystem(i.getId());
					boolean canUpdate = allowedActions.isEditable()
							&& constraintMap.getOrDefault(Permission.UPDATE, new PermissionConstraint(null, null)).allowsITSystem(i.getId());
					boolean canDelete = allowedActions.isDeletable()
							&& constraintMap.getOrDefault(Permission.DELETE, new PermissionConstraint(null, null)).allowsITSystem(i.getId())
							&& !(i.getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
							|| systemType == ItSystemType.KOMBIT
							|| systemType == ItSystemType.KSPCICS
							|| systemType == ItSystemType.NEMLOGIN);
					ItemPermissionDTO specificAllowedActions = new ItemPermissionDTO(canCreate, canRead, canUpdate, canDelete);
                    final Set<ApprovableBy> approvers = new HashSet<>(i.getApproverPermission());
                    final Set<RequestableBy> requesters = new HashSet<>(i.getRequesterPermission());
                    if (approvers.contains(ApprovableBy.INHERIT)) {
                        approvers.addAll(globalApprovables);
                        approvers.remove(ApprovableBy.INHERIT);
                    }
                    if (requesters.contains(RequestableBy.INHERIT)) {
                        requesters.addAll(globalRequestables);
                        requesters.remove(RequestableBy.INHERIT);
                    }
					return new ITSystemListDTO(
							i.getId(),
							i.getName(),
							i.isHidden(),
							i.getIdentifier(),
							i.getSystemType(),
							i.isAccessBlocked(),
							i.isPaused(),
							mappingsForLabel.get(i.getId()),
                            requesters,
                            approvers,
							specificAllowedActions
					);
				})
				.toList();
		model.addAttribute("itsystems", listDTOS);

		return "itsystem/list";
	}

	@RequirePermission( section = Section.IT_SYSTEM, permission = Permission.CREATE)
	@GetMapping(value = { "/ui/itsystem/newad" })
	public String createGetAD(Model model) {
		ItSystemForm form = new ItSystemForm();
		form.setSystemType(ItSystemType.AD);
		model.addAttribute("itSystemForm", form);
		model.addAttribute("domains", domainService.getAll());

		return "itsystem/new";
	}

	@RequirePermission( section = Section.IT_SYSTEM, permission = Permission.CREATE)
	@GetMapping(value = { "/ui/itsystem/newsaml" })
	public String createGetSAML(Model model) {
		ItSystemForm form = new ItSystemForm();
		form.setSystemType(ItSystemType.SAML);
		model.addAttribute("itSystemForm", form);

		return "itsystem/new";
	}

	@RequirePermission( section = Section.IT_SYSTEM, permission = Permission.CREATE)
	@GetMapping(value = { "/ui/itsystem/newmanual" })
	public String createGetManual(Model model) {
		ItSystemForm form = new ItSystemForm();
		form.setSystemType(ItSystemType.MANUAL);
		model.addAttribute("itSystemForm", form);

		return "itsystem/new";
	}

	@RequirePermission( section = Section.IT_SYSTEM, permission = Permission.CREATE)
	@PostMapping("/ui/itsystem/new")
	public String createItSystem(Model model, @Valid @ModelAttribute("itSystemForm") ItSystemForm itSystemForm, BindingResult bindingResult) throws Exception {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("itSystemForm", itSystemForm);
			model.addAttribute("domains", domainService.getAll());

			return "itsystem/new";
		}

		Domain domain = domainService.getByName(itSystemForm.getDomain());
		if (itSystemForm.getSystemType() == ItSystemType.AD && domain == null) {
			log.warn("Missing Domain on creating new AD it-system");
			model.addAttribute("domains", domainService.getAll());

			return "itsystem/new";
		}

		ItSystem itSystem = new ItSystem();
		itSystem.setName(itSystemForm.getName());
		itSystem.setIdentifier(itSystemForm.getIdentifier());
		itSystem.setEmail(itSystemForm.getEmail());

		// can be null
		userService.getOptionalByUuid(itSystemForm.getSelectedResponsibleUuid())
				.ifPresent(itSystem::setAttestationResponsible);

		switch (itSystemForm.getSystemType()) {
			case AD:
				itSystem.setSystemType(ItSystemType.AD);
				itSystem.setPaused(true);
				itSystem.setDomain(domain);
				break;
			case SAML:
				itSystem.setSystemType(ItSystemType.SAML);
				break;
			case MANUAL:
				itSystem.setSystemType(ItSystemType.MANUAL);
				break;
			default:
				throw new Exception("Unknown systemtype: " + itSystemForm.getSystemType());
		}

		itSystem = itSystemService.save(itSystem);

		return "redirect:edit/" + itSystem.getId();
	}

	public record UserRoleListDTO(
		Long id,
		String name,
		String description,
		String delegatedFromCvr,
		boolean readOnly,
		boolean userOnly,
		boolean sensitiveRole,
		boolean extraSensitiveRole,
		boolean roleAssignmentAttestationByAttestationResponsible,
		List<RequestableBy> requesterPermission,
		List<ApprovableBy> approverPermission,
		ItemPermissionDTO allowedActions
	) {}
	@GetMapping("/ui/itsystem/view/{id}")
	public String viewItSystem(Model model, @PathVariable("id") long id) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return "redirect:../list";
		}
		SystemRoleForm systemRoleForm = new SystemRoleForm();
		systemRoleForm.setItSystemId(itSystem.getId());

		List<SystemRoleViewModel> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
				.map(sr -> new SystemRoleViewModel(sr, systemRoleService.isInUse(sr)))
				.collect(Collectors.toList());

		model.addAttribute("itsystem", itSystem);
		model.addAttribute("systemRoles", systemRoles);
		model.addAttribute("userRoles", userRoleService.getByItSystem(itSystem).stream()
				.map(userRole -> new UserRoleListDTO(
						userRole.getId(),
						userRole.getName(),
						userRole.getDescription(),
						userRole.getDelegatedFromCvr(),
						userRole.isReadOnly(),
						userRole.isUserOnly(),
						userRole.isSensitiveRole(),
						userRole.isExtraSensitiveRole(),
						userRole.isRoleAssignmentAttestationByAttestationResponsible(),
						userRole.getRequesterPermission(),
						userRole.getApproverPermission(),
						userPermissionContext.getSpecificAllowedActionsForITsystem(Section.USER_ROLE, itSystem.getId())
				))
				.toList()
		);
		model.addAttribute("attestationResponsibleName", itSystem.getAttestationResponsible() == null ? "" : itSystem.getAttestationResponsible().getName() + " (" + itSystem.getAttestationResponsible().getUserId() + ")");
		model.addAttribute("systemOwnerName", itSystem.getSystemOwner() == null ? "" : itSystem.getSystemOwner().getName() + " (" + itSystem.getSystemOwner().getUserId() + ")");

		Optional<ItSystemMaster> subscribedTo = itSystemMasterService.findAll().stream().filter(its -> Objects.equals(its.getMasterId(), itSystem.getSubscribedTo())).findAny();
		model.addAttribute("subscribedTo", subscribedTo.isPresent() ? subscribedTo.get().getName() : "");
		model.addAttribute("attestationEnabled", settingsService.isScheduledAttestationEnabled());

		return "itsystem/view";
	}

	@RequirePermission(section = Section.IT_SYSTEM, permission = Permission.UPDATE)
	@GetMapping("/ui/itsystem/edit/{id}")
	public String editItSystem(Model model, @PathVariable("id") long id) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return "redirect:../list";
		}
		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraints = constraintMap.getOrDefault(Permission.READ, new PermissionConstraint(null, null));

		if (!readConstraints.allowsITSystem(id)) {
			return "redirect:../list";
		}

		SystemRoleForm systemRoleForm = new SystemRoleForm();
		systemRoleForm.setItSystemId(itSystem.getId());

		List<SystemRoleViewModel> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
				.map(sr -> new SystemRoleViewModel(sr, systemRoleService.isInUse(sr)))
				.collect(Collectors.toList());

		ConvertSystemRolesForm convertSystemRolesForm = new ConvertSystemRolesForm();
		convertSystemRolesForm.setConvertOption(SystemRoleLinkType.NAME_AND_DESCRIPTION);

		Map<Permission, PermissionConstraint> orgunitConstraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readOrgunitConstraint = orgunitConstraintMap.getOrDefault(Permission.READ, new PermissionConstraint(null, null));

		List<OUListForm> ouListForms = orgUnitService.getAll().stream()
				.filter(ou -> readOrgunitConstraint.allowsOrgunit(ou.getUuid()))
				.map(ou -> new OUListForm(ou, false))
				.toList();

		List<String> selectedOus =itSystem.getOrgUnitFilterOrgUnits().stream()
				.map(OrgUnit::getUuid)
				.filter(readOrgunitConstraint::allowsOrgunit)
				.toList();

		Map<Permission, PermissionConstraint> systemConstraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readSystemConstraint = systemConstraintMap.getOrDefault(Permission.READ, new PermissionConstraint(null, null));
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem).stream()
				.filter(ur -> readSystemConstraint.allowsITSystem(ur.getItSystem().getId()))
				.toList();

		model.addAttribute("userRoles", userRoles.stream()
			.map(userRole -> new UserRoleListDTO(
				userRole.getId(),
				userRole.getName(),
				userRole.getDescription(),
				userRole.getDelegatedFromCvr(),
				userRole.isReadOnly(),
				userRole.isUserOnly(),
				userRole.isSensitiveRole(),
				userRole.isExtraSensitiveRole(),
				userRole.isRoleAssignmentAttestationByAttestationResponsible(),
				userRole.getRequesterPermission(),
				userRole.getApproverPermission(),
				userPermissionContext.getSpecificAllowedActionsForITsystem(Section.USER_ROLE, itSystem.getId())
			))
			.toList()
		);

		model.addAttribute("unusedCount", itSystemService.getUnusedUserRolesCount(itSystem));
		model.addAttribute("itsystem", itSystem);
		model.addAttribute("systemRoles", systemRoles);
		model.addAttribute("unusedSystemRolesCount", systemRoles.stream().filter(sr -> !sr.isInUse()).count());
		model.addAttribute("systemRoleForm", systemRoleForm);
		model.addAttribute("itsystemMasterList", itSystemMasterService.findAll());
		model.addAttribute("convertSystemRolesForm", convertSystemRolesForm);
		model.addAttribute("allOUs", ouListForms);
		model.addAttribute("selectedOUs", selectedOus);
		model.addAttribute("attestationResponsibleName", itSystem.getAttestationResponsible() == null ? "" : itSystem.getAttestationResponsible().getName() + " (" + itSystem.getAttestationResponsible().getUserId() + ")");
		model.addAttribute("attestationResponsibleUuid", itSystem.getAttestationResponsible() == null ? "" : itSystem.getAttestationResponsible().getUuid());
		model.addAttribute("kitosITSystemId", itSystem.getKitosITSystem() == null || !configuration.getIntegrations().getKitos().isEnabled() ? null : itSystem.getKitosITSystem().getId());
		model.addAttribute("select2KitosITSystems", select2Service.getKitosITSystemList());
		model.addAttribute("attestationEnabled", settingsService.isScheduledAttestationEnabled());
		model.addAttribute("systemOwnerName", itSystem.getSystemOwner() == null ? "" : itSystem.getSystemOwner().getName() + " (" + itSystem.getSystemOwner().getUserId() + ")");
		model.addAttribute("systemOwnerUuid", itSystem.getSystemOwner() == null ? "" : itSystem.getSystemOwner().getUuid());

		return "itsystem/edit";
	}

	@RequirePermission(section = Section.IT_SYSTEM, permission = Permission.UPDATE)
	@PostMapping("/ui/itsystem/edit/{systemid}/addSystemRole")
	public String addSystemRoleToItSystem(Model model, @PathVariable("systemid") long systemId, @Valid @ModelAttribute("systemRoleForm") SystemRoleForm systemRoleForm, BindingResult bindingResult) {
		ItSystem itSystem = itSystemService.getById(systemId);
		if (itSystem == null || (!itSystem.getSystemType().equals(ItSystemType.AD) && !itSystem.getSystemType().equals(ItSystemType.SAML) && !itSystem.getSystemType().equals(ItSystemType.MANUAL))) {
			return "redirect:../../list";
		}

		if (systemRoleForm.getIdentifier().isEmpty()) {
			bindingResult.addError(new ObjectError("identifier", "html.errors.systemrole.identifier.notempty"));
		}

		if (bindingResult.hasErrors()) {
			List<SystemRoleViewModel> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
					.map(sr -> new SystemRoleViewModel(sr, systemRoleService.isInUse(sr)))
					.collect(Collectors.toList());

			ConvertSystemRolesForm convertSystemRolesForm = new ConvertSystemRolesForm();
			convertSystemRolesForm.setConvertOption(SystemRoleLinkType.NAME_AND_DESCRIPTION);

			model.addAttribute("userRoles", userRoleService.getByItSystem(itSystem).stream()
				.map(userRole -> new UserRoleListDTO(
					userRole.getId(),
					userRole.getName(),
					userRole.getDescription(),
					userRole.getDelegatedFromCvr(),
					userRole.isReadOnly(),
					userRole.isUserOnly(),
					userRole.isSensitiveRole(),
					userRole.isExtraSensitiveRole(),
					userRole.isRoleAssignmentAttestationByAttestationResponsible(),
					userRole.getRequesterPermission(),
					userRole.getApproverPermission(),
					userPermissionContext.getSpecificAllowedActionsForITsystem(Section.USER_ROLE, itSystem.getId())
				))
				.toList()
			);

			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("unusedCount", itSystemService.getUnusedUserRolesCount(itSystem));
			model.addAttribute("itsystem", itSystem);
			model.addAttribute("systemRoles", systemRoles);
			model.addAttribute("unusedSystemRolesCount", systemRoles.stream().filter(sr -> !sr.isInUse()).count());
			model.addAttribute("systemRoleForm", systemRoleForm);
			model.addAttribute("itsystemMasterList", itSystemMasterService.findAll());
			model.addAttribute("convertSystemRolesForm", convertSystemRolesForm);
			model.addAttribute("attestationEnabled", settingsService.isScheduledAttestationEnabled());

			return "itsystem/edit";
		}

		SystemRole systemRole = new SystemRole();
		if (systemRoleForm.getId() > 0) {
			systemRole = systemRoleService.getById(systemRoleForm.getId());

			// only name, description and maximumAssignments can be edited on existing system roles
			systemRole.setName(systemRoleForm.getName());
			systemRole.setDescription(systemRoleForm.getDescription());
			systemRole.setMaximumAssignments(systemRoleForm.getMaximumAssignments());
		}
		else {
			systemRole.setName(systemRoleForm.getName());
			systemRole.setIdentifier(systemRoleForm.getIdentifier().trim());
			systemRole.setDescription(systemRoleForm.getDescription());
			systemRole.setItSystem(itSystem);
			systemRole.setRoleType(RoleType.BOTH);
			systemRole.setWeight(1);
			systemRole.setMaximumAssignments(systemRoleForm.getMaximumAssignments());
		}

		if (itSystem.getSystemType().equals(ItSystemType.AD) || itSystem.getSystemType().equals(ItSystemType.SAML)) {
			systemRole.setWeight(systemRoleForm.getWeight());
		}

		systemRole = systemRoleService.save(systemRole);
		pendingADUpdateService.addSystemRole(systemRole, systemRoleForm.getAdGroupType(), systemRoleForm.isUniversal());

		return "redirect:";
	}

	@RequirePermission(section = Section.IT_SYSTEM, permission = Permission.UPDATE)
	@PostMapping("/ui/itsystem/systemrole/convert/{id}")
	public String convertSystemRoles(Model model, @PathVariable("id") long id, @ModelAttribute("convertSystemRolesForm") ConvertSystemRolesForm convertSystemRolesForm) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return "redirect:../../list";
		}

		if (itSystem.getSystemType().equals(ItSystemType.AD) && itSystem.isReadonly()) {
			return "redirect:../../list";
		}

		if (itSystem.getSystemType().equals(ItSystemType.KSPCICS)) {
			return "redirect:../../list";
		}

		List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
				.filter(sr -> !systemRoleService.isInUse(sr))
				.toList();

		for (SystemRole systemRole : systemRoles) {
			UserRole userRole = new UserRole();
			userRole.setName(convertSystemRolesForm.getPrefix() + systemRole.getName());
			userRole.setApproverPermission(Collections.singletonList(ApprovableBy.INHERIT));
			userRole.setRequesterPermission(Collections.singletonList(RequestableBy.INHERIT));

			// TODO: must be a better way to safely ensure this length max (maybe setter on UserRole)
			// TODO TODO: KBP - Yes there is multiple.... but for now I have to hotfix this, added to stuff that needs refactor
			if (userRole.getName().length() > 128) {
				userRole.setName(userRole.getName().substring(0, 128));
			}

			userRole.setDescription(systemRole.getDescription());
			userRole.setIdentifier("id-" + UUID.randomUUID());
			userRole.setItSystem(itSystem);
			userRole.setSystemRoleAssignments(new ArrayList<>());

			if (convertSystemRolesForm.getConvertOption() != null && !SystemRoleLinkType.NONE.equals(convertSystemRolesForm.getConvertOption())) {
				userRole.setLinkedSystemRole(systemRole);
				userRole.setLinkedSystemRolePrefix(convertSystemRolesForm.getPrefix());
				userRole.setSystemRoleLinkType(convertSystemRolesForm.getConvertOption());
			}

			userRole = userRoleService.save(userRole);

			SystemRoleAssignment roleAssignment = new SystemRoleAssignment();
			roleAssignment.setSystemRole(systemRole);
			roleAssignment.setUserRole(userRole);
			roleAssignment.setAssignedByName(SecurityUtil.getUserFullname());
			roleAssignment.setAssignedByUserId(SecurityUtil.getUserId());
			roleAssignment.setAssignedTimestamp(new Date());

			userRoleService.addSystemRoleAssignment(userRole, roleAssignment);
			userRoleService.save(userRole);
		}

		return "redirect:../../edit/" + itSystem.getId();
	}
}
