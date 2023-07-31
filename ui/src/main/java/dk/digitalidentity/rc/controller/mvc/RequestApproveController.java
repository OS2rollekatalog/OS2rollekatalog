package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import dk.digitalidentity.rc.controller.mvc.viewmodel.RequestForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleDTO;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.service.ItSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.rc.config.SessionConstants;
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

	@Autowired
	private ItSystemService itSystemService;
	
	@RequireAssignerRole
	@GetMapping("/ui/users/requests/authorizationmanager")
	public String getRequests(Model model, HttpServletRequest request) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}
		
		User user = userService.getByUserId(SecurityUtil.getUserId());
		model.addAttribute("requests", requestApproveService.getPendingRequestsAuthorizationManager());
		model.addAttribute("userUuid", (user != null) ? user.getUuid() : "");

		// update badge in UI
		long count = requestApproveService.getPendingRequestsAuthorizationManager().size();
		request.getSession().setAttribute(SessionConstants.SESSION_REQUEST_COUNT, count);
		
		return "request_approve/requests_list";
	}

	@RequireRequesterRole
	@GetMapping("/ui/requestapprove/request/step1/role/{id}")
	public String requestRoleStep1(Model model, @PathVariable long id, @RequestParam(name = "type") String roleType) {		
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}
		
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable to find user for user id: " + SecurityUtil.getUserId());
			return "redirect:/error";
		}

		List<OrgUnit> orgUnits = orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(user);
		
		if (roleType.equals("roleGroup")) {
			RoleGroup roleGroup = roleGroupService.getById(id);
			if (roleGroup == null || !roleGroup.isCanRequest()) {
				return "redirect:/error";
			}

			model.addAttribute("role", roleGroup);

			for (RoleGroupUserRoleAssignment assignment : roleGroup.getUserRoleAssignments()) {
				if (assignment.getUserRole().getItSystem().isOuFilterEnabled()) {
					continue;
				}
				List<String> ouFilterUuids = itSystemService.getOUFilterUuidsWithChildren(assignment.getUserRole().getItSystem());
				orgUnits = orgUnits.stream().filter(o -> ouFilterUuids.contains(o.getUuid())).toList();
			}
		}
		else if (roleType.equals("userRole")) {
			UserRole userRole = userRoleService.getById(id);
			if (userRole == null || !userRole.isCanRequest()) {
				return "redirect:/error";
			}

			model.addAttribute("role", userRole);

			if (userRole.getItSystem().isOuFilterEnabled()) {
				List<String> ouFilterUuids = itSystemService.getOUFilterUuidsWithChildren(userRole.getItSystem());
				orgUnits = orgUnits.stream().filter(o -> ouFilterUuids.contains(o.getUuid())).toList();
			}
		}
		else {
			log.warn("Unknown role type: " + roleType);

			return "redirect:/error";
		}
		
		model.addAttribute("roleType", roleType);
		model.addAttribute("orgUnits", orgUnits);
		
		return "request_approve/choose_ou";
	}

	@SuppressWarnings("deprecation")
	@RequireRequesterRole
	@GetMapping("/ui/requestapprove/request/step2/role/{id}/ou/{ouUuid}")
	public String requestRole(Model model, @PathVariable long id, @PathVariable String ouUuid, @RequestParam(name = "type") String roleType) {		
		if (!settingsService.isRequestApproveEnabled()) {
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
			if (roleGroup == null || !roleGroup.isCanRequest()) {
				return "redirect:/error";
			}

			model.addAttribute("role", roleGroup);
			
			usersWithRole = userService.getUsersWithRoleGroup(roleGroup, false);
		}
		else if (roleType.equals("userRole")) {
			UserRole userRole = userRoleService.getById(id);
			if (userRole == null || !userRole.isCanRequest()) {
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

	@RequireRequesterRole
	@GetMapping("/ui/requestapprove/users")
	public String requestRoleForUser(Model model) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable to find user for user id: " + SecurityUtil.getUserId());
			return "redirect:/error";
		}

		List<RequestUserDTO> userDTOS = new ArrayList<>();
		List<String> addedUsers = new ArrayList<>();
		List<OrgUnit> orgUnits = orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(user);

		for (OrgUnit orgUnit : orgUnits) {
			List<User> users = userService.findByOrgUnit(orgUnit);

			List<RequestUserDTO> dtos = users.stream().map(u -> RequestUserDTO.builder()
							.checked(false)
							.name(u.getName())
							.userId(u.getUserId())
							.uuid(u.getUuid())
							.title(u.getPositions().stream().filter(p -> Objects.equals(orgUnit.getUuid(), p.getOrgUnit().getUuid())).map(p -> p.getName()).findFirst().orElse(""))
							.build())
					.collect(Collectors.toList());

			for (RequestUserDTO dto : dtos) {
				if (!addedUsers.contains(dto.getUuid())) {
					userDTOS.add(dto);
					addedUsers.add(dto.getUuid());
				}
			}
		}

		model.addAttribute("users", userDTOS);

		return "request_approve/list_users";
	}

	@RequireRequesterRole
	@GetMapping("/ui/requestapprove/users/{uuid}/roles")
	public String requestRoleForUser(Model model, @PathVariable String uuid) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			log.warn("Unable to find user for user id: " + SecurityUtil.getUserId());
			return "redirect:/error";
		}

		User user = userService.getByUuid(uuid);
		if (user == null) {
			log.warn("Unable to find user for user uuid: " + uuid);
			return "redirect:/error";
		}
		
		List<OrgUnit> userOrgUnits = user.getPositions().stream().map(Position::getOrgUnit).toList();
		List<UserRole> userRoles = userRoleService.getAllRequestable();
		List<RoleGroup> roleGroups = roleGroupService.getAllRequestable();

		// filter list of users orgunits, so we only get those that the current manager/authorizer is responsible for
		List<OrgUnit> requesterOrgUnits = orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(loggedInUser);
		userOrgUnits = userOrgUnits.stream().filter(uo -> requesterOrgUnits.stream().anyMatch(ro -> Objects.equals(ro.getUuid(), uo.getUuid()))).collect(Collectors.toList());
		
		if (userOrgUnits.size() == 0) {
			log.warn("Attempting to request roles for user that manager is not allowed to request for, manager = " + loggedInUser.getUserId() + ", user=" + user.getUserId());
			return "redirect:/ui/my/requests";
		}

		Iterator<UserRole> userRoleIterator = userRoles.iterator();
		while (userRoleIterator.hasNext()) {
			UserRole userRole = userRoleIterator.next();
			List<String> ouUuids = itSystemService.getOUFilterUuidsWithChildren(userRole.getItSystem());
			
			if (!ouUuids.isEmpty()) {
				if (userOrgUnits.stream().noneMatch(u -> ouUuids.contains(u.getUuid()))) {
					userRoleIterator.remove();
				}
			}
		}

		Iterator<RoleGroup> roleGroupIterator = roleGroups.iterator();
		while (roleGroupIterator.hasNext()) {
			RoleGroup roleGroup = roleGroupIterator.next();
			boolean shouldRemove = false;

			for (RoleGroupUserRoleAssignment assignment : roleGroup.getUserRoleAssignments()) {
				List<String> ouUuids = itSystemService.getOUFilterUuidsWithChildren(assignment.getUserRole().getItSystem());

				if (!ouUuids.isEmpty()) {
					if (userOrgUnits.stream().noneMatch(u -> ouUuids.contains(u.getUuid()))) {
						shouldRemove = true;
						break;
					}
				}
			}

			if (shouldRemove) {
				roleGroupIterator.remove();
			}
		}

		List<RoleDTO> roles = new ArrayList<>();
		roles.addAll(userRoles.stream().map(r -> new RoleDTO(r)).toList());
		roles.addAll(roleGroups.stream().map(r -> new RoleDTO(r)).toList());

		model.addAttribute("roles", roles);
		model.addAttribute("user", user);
		model.addAttribute("orgUnitUuid", userOrgUnits.get(0).getUuid()); // a bit of a hack, but better than not having the information
		model.addAttribute("requestForm", new RequestForm(user.getUuid()));

		return "request_approve/list_roles";
	}
}
