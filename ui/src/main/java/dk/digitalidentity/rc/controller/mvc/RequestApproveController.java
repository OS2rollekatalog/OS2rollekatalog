package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.rc.controller.mvc.viewmodel.RequestUserDTO;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireRequesterRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.service.model.WhoCanRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class RequestApproveController {

	@Autowired
	private RequestApproveService requestApproveService;
	
	@Autowired 
	private SettingsService settingsService;
	
	@Autowired 
	private RoleGroupService roleGroupService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private UserService userService;
	
	@RequireAssignerRole
	@GetMapping("/ui/users/requests/authorizationmanager")
	public String getRequests(Model model) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		model.addAttribute("requests", requestApproveService.getPendingRequestsAuthorizationManager());
		
		return "request_approve/requests_list";
	}

	@RequireRequesterRole
	@GetMapping("/ui/requestapprove/request/step1/role/{id}")
	public String requestRoleStep1(Model model, @PathVariable long id, @RequestParam(name = "type") String roleType) {		
		if (!settingsService.getRequestApproveWho().equals(WhoCanRequest.AUTHORIZATION_MANAGER) || !settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}
		
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable to find user for user id: " + SecurityUtil.getUserId());
			return "redirect:/error";
		}
		
		if (roleType.equals("roleGroup")) {
			RoleGroup roleGroup = roleGroupService.getById(id);
			if (roleGroup == null) {
				return "redirect:/error";
			}

			model.addAttribute("role", roleGroup);
		}
		else if (roleType.equals("userRole")) {
			UserRole userRole = userRoleService.getById(id);
			if (userRole == null) {
				return "redirect:/error";
			}

			model.addAttribute("role", userRole);
		}
		else {
			log.warn("Unknown role type: " + roleType);

			return "redirect:/error";
		}
		
		model.addAttribute("roleType", roleType);
		
		List<OrgUnit> orgUnits = orgUnitService.getByAuthorizationManagerOrManagerMatchingUser(user);
		
		model.addAttribute("orgUnits", orgUnits);

		return "request_approve/choose_ou";
	}
	
	@SuppressWarnings("deprecation")
	@RequireRequesterRole
	@GetMapping("/ui/requestapprove/request/step2/role/{id}/ou/{ouUuid}")
	public String requestRole(Model model, @PathVariable long id, @PathVariable String ouUuid, @RequestParam(name = "type") String roleType) {		
		if (!settingsService.getRequestApproveWho().equals(WhoCanRequest.AUTHORIZATION_MANAGER) || !settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}
		
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable to find user for user id: " + SecurityUtil.getUserId());
			return "redirect:/error";
		}
		
		OrgUnit orgUnit = orgUnitService.getByUuid(ouUuid);
		if (orgUnit == null) {
			return "redirect:/error";
		}

		model.addAttribute("orgUnit", orgUnit);
		
		List<UserWithRole> usersWithRole = new ArrayList<>();
		if (roleType.equals("roleGroup")) {
			RoleGroup roleGroup = roleGroupService.getById(id);
			if (roleGroup == null) {
				return "redirect:/error";
			}

			model.addAttribute("role", roleGroup);
			
			usersWithRole = userService.getUsersWithRoleGroup(roleGroup, false);
		}
		else if (roleType.equals("userRole")) {
			UserRole userRole = userRoleService.getById(id);
			if (userRole == null) {
				return "redirect:/error";
			}

			model.addAttribute("role", userRole);
			
			usersWithRole = userService.getUsersWithUserRole(userRole, false);
		}
		else {
			log.warn("Unknown role type: " + roleType);
			return "redirect:/error";
		}
		
		model.addAttribute("roleType", roleType);
		
		List<User> users = userService.findByOrgUnit(orgUnit);
		
		List<RequestUserDTO> userDTOs = users.stream().map(u -> RequestUserDTO.builder()
				.checked(false)
				.name(u.getName())
				.userId(u.getUserId())
				.uuid(u.getUuid())
				.build())
			.collect(Collectors.toList());

		// check and lock those that has the role already
		for (RequestUserDTO userDTO : userDTOs) {
			boolean match = usersWithRole.stream().anyMatch(uwr -> Objects.equals(uwr.getUser().getUuid(), userDTO.getUuid()));
			
			if (match) {
				userDTO.setChecked(true);
				userDTO.setLocked(true);
			}
		}

		model.addAttribute("users", userDTOs);		
		
		return "request_approve/choose_users";
	}
}
