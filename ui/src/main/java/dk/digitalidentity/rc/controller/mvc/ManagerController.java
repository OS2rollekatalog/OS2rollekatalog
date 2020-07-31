package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationFormRaw;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationRemovalSet;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleOrRoleGroupDTO;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.AttestationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@Controller
@RequireAssignerOrManagerRole
public class ManagerController {
	private static final String SESSION_ATTESTATION_FORM = "SESSION_ATTESTATION_FORM";

	@Autowired
	private RequestApproveService requestApproveService;
	
	@Autowired
	private AttestationService attestationService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private PositionService positionService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private SecurityUtil securityUtil;
	
	@RequireAdministratorRole
	@GetMapping("/ui/admin/attestations")
	public String getAttestationsForAdmin() {
		return "manager/admin_attestation_list";
	}

	@RequireManagerRole
	@GetMapping("/ui/manager/substitute")
	public String getSubstitute(Model model) {
		User manager = getManager();
		if (manager == null) {
			return "redirect:/";
		}

		model.addAttribute("substitute", manager.getManagerSubstitute());
		
		return "manager/substitute";
	}

	@GetMapping("/ui/users/requests")
	public String getRequests(Model model) {
		model.addAttribute("requests", requestApproveService.getPendingRequests());
		
		return "manager/requests";
	}

	@RequireManagerRole
	@GetMapping("/ui/users/attestations")
	public String getAttestations(Model model) {
		List<OrgUnit> orgUnitCandidates = orgUnitService.getByManager();

		List<OrgUnit> orgUnits = new ArrayList<>();
		for (OrgUnit orgUnit : orgUnitCandidates) {
			if (attestationService.countByOrgUnit(orgUnit) > 0) {
				orgUnits.add(orgUnit);
			}
		}

		model.addAttribute("orgUnits", orgUnits);

		return "manager/attestations_orgunits";
	}

	@RequireManagerRole
	@GetMapping("/ui/users/attestations/{uuid}")
	public String getAccessRightsAttestation(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);
			
			return "redirect:/ui/users/attestations";
		}

		User manager = getManager();
		if (manager == null) {
			return "redirect:/";
		}

		boolean allowed = true;
		if (orgUnit.getManager() == null) {
			allowed = false;
		}
		else if (!orgUnit.getManager().getUuid().equals(manager.getUuid())) {
			allowed = false;

			// unless the user is a substitute for the manager of this orgunit
			List<User> managers = securityUtil.getManagersBySubstitute();

			for (User m : managers) {
				if (orgUnit.getManager().getUuid().equals(m.getUuid())) {
					allowed = true;
					break;
				}
			}
		}
		
		if (!allowed) {
			log.warn("User '" + manager.getUserId() + "' is not manager for '" + orgUnit.getName() + "' / '" + orgUnit.getUuid() + "'");

			return "redirect:/ui/users/attestations";			
		}
		
		List<User> usersWithDuplicates = attestationService.getByOrgUnit(orgUnit).stream().map(a -> a.getUser()).collect(Collectors.toList());

		Map<User, List<UserRoleOrRoleGroupDTO>> usersAndRolesUnsorted = new LinkedHashMap<>();
		for (User user : usersWithDuplicates) {

			// check for existing entry
			boolean found = false;
			for (User u : usersAndRolesUnsorted.keySet()) {
				if (u.getUuid().equals(user.getUuid())) {
					found = true;
					break;
				}
			}
			
			if (found) {
				continue;
			}
			
			List<UserRoleOrRoleGroupDTO> roles = new ArrayList<UserRoleOrRoleGroupDTO>();

			List<UserRole> userRoles = user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
			for (UserRole userRole : userRoles) {
				roles.add(new UserRoleOrRoleGroupDTO(userRole));
			}

			List<RoleGroup> roleGroups = user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList());
			for (RoleGroup roleGroup : roleGroups) {
				roles.add(new UserRoleOrRoleGroupDTO(roleGroup));
			}

			for (Position position : user.getPositions()) {
				if (position.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {

					List<UserRole> pur = position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
					for (UserRole userRole : pur) {
						roles.add(new UserRoleOrRoleGroupDTO(userRole));
					}
					
			      	List<RoleGroup> prg = position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList());
					for (RoleGroup roleGroup : prg) {
						roles.add(new UserRoleOrRoleGroupDTO(roleGroup));
					}					
				}
			}
			
			if (roles.size() > 0) {
				usersAndRolesUnsorted.put(user, roles);
			}
		}

		Map<User, List<UserRoleOrRoleGroupDTO>> usersAndRolesSorted = usersAndRolesUnsorted.entrySet().stream()
				.sorted(Map.Entry.comparingByKey((o1, o2)-> o1.getName().compareTo(o2.getName())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
				(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		
		model.addAttribute("orgUnit", orgUnit);
		model.addAttribute("usersAndRoles", usersAndRolesSorted);

		return "manager/attestations_users";
	}

	@RequireManagerRole
	@PostMapping("/ui/users/attestations/confirm")
	public String confirmRoleRemoval(Model model, AttestationFormRaw form, HttpServletRequest request, RedirectAttributes redirectAttributes) {		
		User manager = getManager();
		if (manager == null) {
			return "redirect:/";
		}

		OrgUnit orgUnit = orgUnitService.getByUuid(form.getOrgUnitUuid());
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + form.getOrgUnitUuid());
			
			return "redirect:/ui/users/attestations";
		}
		else {
			
			boolean allowed = true;
			if (orgUnit.getManager() == null) {
				allowed = false;
			}
			else if (!orgUnit.getManager().getUuid().equals(manager.getUuid())) {
				allowed = false;

				// unless the user is a substitute for the manager of this orgunit
				List<User> managers = securityUtil.getManagersBySubstitute();

				for (User m : managers) {
					if (orgUnit.getManager().getUuid().equals(m.getUuid())) {
						allowed = true;
						break;
					}
				}
			}

			if (!allowed) {
				log.warn("User '" + manager.getUserId() + "' is not manager for '" + orgUnit.getName() + "' / '" + orgUnit.getUuid() + "'");
				
				return "redirect:/ui/users/attestations";
			}
		}

		Map<String, AttestationRemovalSet> data = null;
		try {
			ObjectMapper om = new ObjectMapper();

			data = om.readValue(form.getData(), new TypeReference<HashMap<String, AttestationRemovalSet>>() { });
		}
		catch (Exception ex) {
			log.error("Failed to convert input", ex);

			return "redirect:/ui/users/attestations";
		}

		AttestationForm attestationForm = new AttestationForm();
		attestationForm.setOrgUnitUuid(form.getOrgUnitUuid());
		attestationForm.setData(data);

		// no changes, just skip straight to receipt
		if (attestationForm.getData().size() == 0) {
			performCleanup(attestationForm, orgUnit, manager);

			redirectAttributes.addFlashAttribute("success", "true");
			return "redirect:/ui/users/attestations";
		}

		// store on session so we know what is being attested
		request.getSession().setAttribute(SESSION_ATTESTATION_FORM, attestationForm);

		model.addAttribute("orgUnitUuid", attestationForm.getOrgUnitUuid());
		model.addAttribute("roleMap", getRoleMap(attestationForm.getData()));

		return "manager/attestations_confirm";
	}

	@RequireManagerRole
	@PostMapping("/ui/users/attestations/remove")
	public String removal(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes) {
		User manager = getManager();
		if (manager == null) {
			return "redirect:/";
		}		

		Object o = request.getSession().getAttribute(SESSION_ATTESTATION_FORM);
		if (o == null || !(o instanceof AttestationForm)) {
			log.warn("No attestation form present on session!");

			return "redirect:/ui/users/attestations";
		}

		AttestationForm form = (AttestationForm) o;

		OrgUnit orgUnit = orgUnitService.getByUuid(form.getOrgUnitUuid());
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + form.getOrgUnitUuid());
			
			return "redirect:/ui/users/attestations";
		}
		else {
			if (orgUnit.getManager() == null || !orgUnit.getManager().getUuid().equals(manager.getUuid())) {
				log.warn("User '" + manager.getUserId() + "' is not manager for '" + orgUnit.getName() + "' / '" + orgUnit.getUuid() + "'");
				
				return "redirect:/ui/users/attestations";
			}
		}

		performCleanup(form, orgUnit, manager);

		redirectAttributes.addFlashAttribute("success", "true");

		return "redirect:/ui/users/attestations";
	}

	private void performCleanup(AttestationForm form, OrgUnit orgUnit, User manager) {		
		Map<User, List<UserRoleOrRoleGroupDTO>> roleMap = getRoleMap(form.getData());

		SecurityUtil.loginSystemAccount();

		try {
			for (User user : roleMap.keySet()) {
				for (UserRoleOrRoleGroupDTO row : roleMap.get(user)) {
					if (row.isUserRole()) {
						userService.removeUserRole(user, row.getUserRole());
						
						for (Position position : user.getPositions()) {
							if (position.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
								positionService.removeUserRole(position, row.getUserRole());
							}
						}
					}
					else {
						userService.removeRoleGroup(user, row.getRoleGroup());
	
						for (Position position : user.getPositions()) {
							if (position.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
								positionService.removeRoleGroup(position, row.getRoleGroup());
							}
						}					
					}
				}
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}

		attestationService.deleteByOrgUnit(orgUnit);

		String description = null;
		if (orgUnit.getManager() != null && !orgUnit.getManager().getUuid().equals(manager.getUuid())) {
			description = "Udført af stedfortræder for " + orgUnit.getManager().getName();
		}
		
		orgUnit.setLastAttested(new Date());
		orgUnit.setLastAttestedBy(manager.getName());
		orgUnit.setNextAttestation(null);
		orgUnitService.save(orgUnit);
		
		auditLogger.log(orgUnit, EventType.ATTESTED_ORGUNIT, description);
	}

	private Map<User, List<UserRoleOrRoleGroupDTO>> getRoleMap(Map<String, AttestationRemovalSet> data) {
		Map<User, List<UserRoleOrRoleGroupDTO>> result = new HashMap<>();
		
		for (String uuid : data.keySet()) {
			User user = userService.getByUuid(uuid);
			if (user == null) {
				log.warn("User does not exist: " + uuid);
				continue;
			}

			AttestationRemovalSet set = data.get(uuid);
			List<UserRoleOrRoleGroupDTO> list = new ArrayList<>();

			for (Long userRoleId : set.getUserRoles()) {
				UserRole userRole = userRoleService.getById(userRoleId);
				if (userRole == null) {
					log.warn("UserRole does not exist: " + userRoleId);
					continue;
				}
				
				list.add(new UserRoleOrRoleGroupDTO(userRole));
			}

			for (Long roleGroupId : set.getRoleGroups()) {
				RoleGroup roleGroup = roleGroupService.getById(roleGroupId);
				if (roleGroup == null) {
					log.warn("RoleGroup does not exist: " + roleGroupId);
					continue;
				}
				
				list.add(new UserRoleOrRoleGroupDTO(roleGroup));
			}
			
			result.put(user, list);
		}
		
		return result;
	}

	private User getManager() {
		return userService.getByUserId(SecurityUtil.getUserId());
	}
}
