package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ConvertSystemRolesForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ItSystemForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleViewModel;
import dk.digitalidentity.rc.controller.validator.ItSystemValidator;
import dk.digitalidentity.rc.controller.validator.SystemRoleValidator;
import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemMaster;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ADConfigurationService;
import dk.digitalidentity.rc.service.ClientService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequireReadAccessRole
@Controller
public class ItSystemController {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private ItSystemValidator itSystemValidator;

	@Autowired
	private SystemRoleValidator systemRoleValidator;

	@Autowired
	private ItSystemMasterService itSystemMasterService;
	
	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private UserService userService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private ADConfigurationService adConfigurationService;

	@Autowired
	private Select2Service select2Service;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@InitBinder(value = { "itSystemForm" })
	public void initBinderItSystemForm(WebDataBinder binder) {
		binder.addValidators(itSystemValidator);
	}

	@InitBinder(value = { "systemRoleForm" })
	public void initBinderSystemRoleForm(WebDataBinder binder) {
		binder.addValidators(systemRoleValidator);
	}

	record ITSystemListDTO(long id, String name, boolean hidden, String identifier, ItSystemType systemType, boolean accessBlocked, boolean paused, List<String> adSyncServiceLabels) {}
	@GetMapping(value = { "/ui/itsystem", "/ui/itsystem/list" })
	public String list(Model model) {
		List<ItSystem> itSystems = itSystemService.getAll();

		// people with restricted read-only access will be limited
		if (securityUtil.hasRestrictedReadAccess()) {
			List<Long> itSystemIds = securityUtil.getRestrictedReadAccessItSystems();
			
			itSystems = itSystems.stream().filter(it -> itSystemIds.contains(it.getId())).collect(Collectors.toList());
		}

		itSystems = itSystems.stream().filter(its -> its.isDeleted() == false).collect(Collectors.toList());

		Map<Long, List<String>> mappingsForLabel = getMappingsForLabel();
		List<ITSystemListDTO> listDTOS = itSystems.stream().map(i -> new ITSystemListDTO(i.getId(), i.getName(), i.isHidden(), i.getIdentifier(), i.getSystemType(), i.isAccessBlocked(), i.isPaused(), mappingsForLabel.get(i.getId()))).collect(Collectors.toList());
		model.addAttribute("itsystems", listDTOS);

		return "itsystem/list";
	}

	private Map<Long, List<String>> getMappingsForLabel() {
		Map<Long, List<String>> result = new HashMap<>();
		List<Client> clients = clientService.findADSyncServices();
		for (Client client : clients) {
			ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
			if (adConfiguration != null && adConfiguration.getJson() != null && adConfiguration.getJson().getItSystemGroupFeatureSystemMap() != null) {
				for (String mapping : adConfiguration.getJson().getItSystemGroupFeatureSystemMap()) {
					try {
						String[] split = mapping.split(";");
						long id = Long.parseLong(split[0]);
						String dn = split[1];
						String cn = "";
						for (String part : dn.split(",")) {
							if (part.startsWith("CN=")) {
								cn = part.substring(3);
								break;
							}
						}

						if (!result.containsKey(id)) {
							result.put(id, new ArrayList<>());
						}

						result.get(id).add("ADGruppe: " + cn);
					} catch (Exception ex) {
						log.warn("Possibly malformed ItSystemGroupFeatureSystemMap mapping: " + mapping);
					}
				}
			}
		}

		return result;
	}

	@RequireAdministratorRole
	@GetMapping(value = { "/ui/itsystem/newad" })
	public String createGetAD(Model model) {
		ItSystemForm form = new ItSystemForm();
		form.setSystemType(ItSystemType.AD);
		model.addAttribute("itSystemForm", form);
		model.addAttribute("domains", domainService.getAll());
		
		return "itsystem/new";
	}
	
	@RequireAdministratorRole
	@GetMapping(value = { "/ui/itsystem/newsaml" })
	public String createGetSAML(Model model) {
		ItSystemForm form = new ItSystemForm();
		form.setSystemType(ItSystemType.SAML);
		model.addAttribute("itSystemForm", form);
		
		return "itsystem/new";
	}

	@RequireAdministratorRole
	@GetMapping(value = { "/ui/itsystem/newmanual" })
	public String createGetManual(Model model) {
		ItSystemForm form = new ItSystemForm();
		form.setSystemType(ItSystemType.MANUAL);
		model.addAttribute("itSystemForm", form);
		
		return "itsystem/new";
	}

	@RequireAdministratorRole
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
		User user = userService.getByUuid(itSystemForm.getSelectedResponsibleUuid());
		itSystem.setAttestationResponsible(user);

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
		model.addAttribute("userRoles", userRoleService.getByItSystem(itSystem));
		model.addAttribute("attestationResponsibleName", itSystem.getAttestationResponsible() == null ? "" : itSystem.getAttestationResponsible().getName() + " (" + itSystem.getAttestationResponsible().getUserId() + ")");
		model.addAttribute("systemOwnerName", itSystem.getSystemOwner() == null ? "" : itSystem.getSystemOwner().getName() + " (" + itSystem.getSystemOwner().getUserId() + ")");

		Optional<ItSystemMaster> subscribedTo = itSystemMasterService.findAll().stream().filter(its -> Objects.equals(its.getMasterId(), itSystem.getSubscribedTo())).findAny();
		model.addAttribute("subscribedTo", subscribedTo.isPresent() ? subscribedTo.get().getName() : "");
		model.addAttribute("attestationEnabled", settingsService.isScheduledAttestationEnabled());

		return "itsystem/view";
	}

	@RequireAdministratorRole
	@GetMapping("/ui/itsystem/edit/{id}")
	public String editItSystem(Model model, @PathVariable("id") long id) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return "redirect:../list";
		}

		SystemRoleForm systemRoleForm = new SystemRoleForm();
		systemRoleForm.setItSystemId(itSystem.getId());

		List<SystemRoleViewModel> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
				.map(sr -> new SystemRoleViewModel(sr, systemRoleService.isInUse(sr)))
				.collect(Collectors.toList());

		ConvertSystemRolesForm convertSystemRolesForm = new ConvertSystemRolesForm();
		convertSystemRolesForm.setCreateLink(true);

		List<OUListForm> ouListForms = orgUnitService.getAll().stream().map(ou -> new OUListForm(ou, false)).toList();

		model.addAttribute("unusedCount", itSystemService.getUnusedUserRolesCount(itSystem));
		model.addAttribute("itsystem", itSystem);
		model.addAttribute("systemRoles", systemRoles);
		model.addAttribute("unusedSystemRolesCount", systemRoles.stream().filter(sr -> !sr.isInUse()).count());
		model.addAttribute("systemRoleForm", systemRoleForm);
		model.addAttribute("itsystemMasterList", itSystemMasterService.findAll());
		model.addAttribute("userRoles", userRoleService.getByItSystem(itSystem));
		model.addAttribute("convertSystemRolesForm", convertSystemRolesForm);
		model.addAttribute("allOUs", ouListForms);
		model.addAttribute("selectedOUs", itSystem.getOrgUnitFilterOrgUnits().stream().map(OrgUnit::getUuid).toList());
		model.addAttribute("attestationResponsibleName", itSystem.getAttestationResponsible() == null ? "" : itSystem.getAttestationResponsible().getName() + " (" + itSystem.getAttestationResponsible().getUserId() + ")");
		model.addAttribute("attestationResponsibleUuid", itSystem.getAttestationResponsible() == null ? "" : itSystem.getAttestationResponsible().getUuid());
		model.addAttribute("kitosITSystemId", itSystem.getKitosITSystem() == null || !configuration.getIntegrations().getKitos().isEnabled() ? null : itSystem.getKitosITSystem().getId());
		model.addAttribute("select2KitosITSystems", select2Service.getKitosITSystemList());
		model.addAttribute("attestationEnabled", settingsService.isScheduledAttestationEnabled());
		model.addAttribute("systemOwnerName", itSystem.getSystemOwner() == null ? "" : itSystem.getSystemOwner().getName() + " (" + itSystem.getSystemOwner().getUserId() + ")");
		model.addAttribute("systemOwnerUuid", itSystem.getSystemOwner() == null ? "" : itSystem.getSystemOwner().getUuid());

		return "itsystem/edit";
	}

	@RequireAdministratorRole
	@PostMapping("/ui/itsystem/edit/{systemid}/addSystemRole")
	public String addSystemRoleToItSystem(Model model, @PathVariable("systemid") long systemId, @Valid @ModelAttribute("systemRoleForm") SystemRoleForm systemRoleForm, BindingResult bindingResult) {
		ItSystem itSystem = itSystemService.getById(systemId);
		if (itSystem == null || (!itSystem.getSystemType().equals(ItSystemType.AD) && !itSystem.getSystemType().equals(ItSystemType.SAML) && !itSystem.getSystemType().equals(ItSystemType.MANUAL))) {
			return "redirect:../../list";
		}

		if (systemRoleForm.getIdentifier().length() == 0) {
			bindingResult.addError(new ObjectError("identifier", "html.errors.systemrole.identifier.notempty"));
		}
		
		if (bindingResult.hasErrors()) {
			List<SystemRoleViewModel> systemRoles = systemRoleService.getByItSystem(itSystem).stream()
					.map(sr -> new SystemRoleViewModel(sr, systemRoleService.isInUse(sr)))
					.collect(Collectors.toList());
			
			ConvertSystemRolesForm convertSystemRolesForm = new ConvertSystemRolesForm();
			convertSystemRolesForm.setCreateLink(true);

			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("unusedCount", itSystemService.getUnusedUserRolesCount(itSystem));
			model.addAttribute("itsystem", itSystem);
			model.addAttribute("systemRoles", systemRoles);
			model.addAttribute("unusedSystemRolesCount", systemRoles.stream().filter(sr -> !sr.isInUse()).count());
			model.addAttribute("systemRoleForm", systemRoleForm);
			model.addAttribute("itsystemMasterList", itSystemMasterService.findAll());
			model.addAttribute("userRoles", userRoleService.getByItSystem(itSystem));
			model.addAttribute("convertSystemRolesForm", convertSystemRolesForm);

			return "itsystem/edit";
		}
		
		SystemRole systemRole = new SystemRole();
		if (systemRoleForm.getId() > 0) {
			systemRole = systemRoleService.getById(systemRoleForm.getId());
			
			// only name and description can be edited on existing system roles
			systemRole.setName(systemRoleForm.getName());
			systemRole.setDescription(systemRoleForm.getDescription());
		}
		else {
			systemRole.setName(systemRoleForm.getName());
			systemRole.setIdentifier(systemRoleForm.getIdentifier());
			systemRole.setDescription(systemRoleForm.getDescription());
			systemRole.setItSystem(itSystem);
			systemRole.setRoleType(RoleType.BOTH);
			systemRole.setWeight(1);
		}
		
		if (itSystem.getSystemType().equals(ItSystemType.AD) || itSystem.getSystemType().equals(ItSystemType.SAML)) {
			systemRole.setWeight(systemRoleForm.getWeight());
		}

		systemRole = systemRoleService.save(systemRole);
		pendingADUpdateService.addSystemRole(systemRole, systemRoleForm.getAdGroupType(), systemRoleForm.isUniversal());

		return "redirect:";
	}

	@RequireAdministratorRole
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
				.filter(sr -> systemRoleService.isInUse(sr) == false)
				.collect(Collectors.toList());

		for (SystemRole systemRole : systemRoles) {
			UserRole userRole = new UserRole();
			userRole.setName(convertSystemRolesForm.getPrefix() + systemRole.getName());
			
			// TODO: must be a better way to safely ensure this length max (maybe setter on UserRole)
			if (userRole.getName().length() > 64) {
				userRole.setName(userRole.getName().substring(0, 64));
			}

			userRole.setDescription(systemRole.getDescription());
			userRole.setIdentifier("id-" + UUID.randomUUID().toString());
			userRole.setItSystem(itSystem);
			userRole.setSystemRoleAssignments(new ArrayList<SystemRoleAssignment>());

			if (convertSystemRolesForm.isCreateLink()) {
				userRole.setLinkedSystemRole(systemRole);
				userRole.setLinkedSystemRolePrefix(convertSystemRolesForm.getPrefix());
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
