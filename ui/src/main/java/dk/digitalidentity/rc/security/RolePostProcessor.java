package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.permission.PermissionService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.ReportTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.SamlLoginPostProcessor;
import dk.digitalidentity.samlmodule.model.TokenUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Component
@Transactional
public class RolePostProcessor implements SamlLoginPostProcessor {
	public static final String ATTRIBUTE_USERID = "ATTRIBUTE_USERID";
	public static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
	public static final String ATTRIBUTE_SUBSTITUTE_FOR = "ATTRIBUTE_SUBSTITUTE_FOR";
	public static final String ATTRIBUTE_CLIENT = "ATTRIBUTE_CLIENT";
	public static final String ATTRIBUTE_USER_UUID = "ATTRIBUTE_USER_UUID";

	private final UserService userService;
	private final AuditLogger auditLogger;
	private final SettingsService settingsService;
	private final ItSystemService itSystemService;
	private final ReportTemplateService reportTemplateService;
	private final NotificationService notificationService;
	private final OrgUnitService orgUnitService;
	private final ManagerDelegateService managerDelegateService;
	private final AccessConstraintService accessConstraintService;
	private final PermissionService permissionService;
	private final AssignmentService assignmentService;

	@Value("${dev.login.impersonate.enabled:false}")
	private boolean impersonateEnabled;

	@Value("${dev.login.impersonate.user:#{null}}")
	private String impersonateUser;

	@Override
	public void process(TokenUser tokenUser) {
		String principal = tokenUser.getUsername();

		User user = resolveUser(tokenUser);
		if (user == null) {
			throw new UsernameNotFoundException("Brugeren " + principal + " er ikke kendt af rollekataloget!");
		}

		auditLogger.log(user, EventType.LOGIN_LOCAL);

		tokenUser.getAttributes().put(ATTRIBUTE_USERID, user.getUserId());
		tokenUser.getAttributes().put(ATTRIBUTE_USER_UUID, user.getUuid());
		tokenUser.getAttributes().put(ATTRIBUTE_NAME, user.getName());

		List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);

		// Find all role identifiers for userroles assigned to this user in the role catalogue
		Set<String> roles = getRoleCatalogueUserRoles(user, itSystems);

		// ---------- Section-specific permission roles -------------------------------
		Map<Section, Map<Permission, PermissionConstraint>> permissionMap = accessConstraintService.constructUserPermissions(user, itSystems.getFirst());
		permissionService.saveUserPermissions(user.getUuid(), permissionMap);
		// ---------------------------------------------------------------------------

		Set<SamlGrantedAuthority> authorities = new HashSet<>();

		// construct authorities for the user
		List<User> managers = handleManagerRoles(user, authorities, tokenUser);
		handleRequestApproveRoles(!managers.isEmpty(), user, authorities);
		handleHierachialRoles(roles, authorities);
		handleNonHierachialRoles(roles, authorities, user);

		tokenUser.setAuthorities(authorities);
	}

	private User resolveUser(TokenUser tokenUser) {
		if (impersonateEnabled && impersonateUser != null) {
			User impersonated = userService.getByUserId(impersonateUser);
			if (impersonated != null) {
				tokenUser.setUsername(impersonated.getUserId());
				return impersonated;
			}
		}
		return userService.getByUserId(tokenUser.getUsername());
	}

	private void setNotifications() {
		HttpServletRequest request = getRequest();

		if (request != null) {
			long count = notificationService.countActive();
			if (count > 0) {
				request.getSession().setAttribute(SessionConstants.SESSION_NOTIFICATION_COUNT, count);
			}
		}
	}

	private static HttpServletRequest getRequest() {
		try {
			return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		}
		catch (IllegalStateException ex) {
			return null;
		}
	}

	/**
	 * Finds all identifiers for userroles assigned to the user in RoleCatalogue it system
	 * @param user a user
	 * @return set of identifiers for assigned role catalogue roles
	 */
	private Set<String> getRoleCatalogueUserRoles(User user, List<ItSystem> itSystems) {
		Set<String> roles = new HashSet<>();
		// Find the role-catalogue it system in db

		// For each system role of user roles on the role catalogue system, add their identifier to the list of roles
		List<UserRole> userRoles = new ArrayList<>(assignmentService.getUserRolesByUserAndSystems(user, itSystems));
		if (userRoles != null) {
			for (UserRole role : userRoles) {
				for (SystemRoleAssignment roleAssignment : role.getSystemRoleAssignments()) {
					roles.add(roleAssignment.getSystemRole().getIdentifier());
				}
			}
		}
		return roles;
	}

	private List<User> handleManagerRoles(User user, Set<SamlGrantedAuthority> authorities, TokenUser tokenUser) {
		boolean isSubstitute = false;
		boolean isDelegate = false;
		boolean isManager = false;

		Set<String> managerOuUuids = new HashSet<>();

		// if any manager has flagged this user as a substitute, add the substitute role and keep track of the list of managers
		List<User> managers = userService.getSubstitutesManager(user);
		if (!managers.isEmpty()) {
			isSubstitute = true;
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_SUBSTITUTE));
			tokenUser.getAttributes().put(ATTRIBUTE_SUBSTITUTE_FOR, managers.stream()
				.map(User::getUuid)
				.toList()
				.toArray(new String[0]));

			// Add substitute OUs
			user.getSubstituteFor().stream()
				.filter(ms -> !ms.getManager().isDeleted() && !ms.getManager().isDisabled())
				.map(ms -> ms.getOrgUnit().getUuid())
				.forEach(managerOuUuids::add);
		}

		// If the current user is delegated for a manager add the role
		List<ManagerDelegate> byDelegate = managerDelegateService.getByDelegate(user);
		if (!byDelegate.isEmpty()) {
			isDelegate = true;
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_MANAGER_SUBSTITUDE));

			// Add delegate OUs
			byDelegate.stream()
				.filter(md -> !md.getManager().isDeleted() && !md.getManager().isDisabled())
				.flatMap(md -> orgUnitService.getByManagerMatchingUser(md.getManager()).stream())
				.map(OrgUnit::getUuid)
				.forEach(managerOuUuids::add);
		}

		// check if the request/approve feature is enabled
		if (settingsService.isRequestApproveEnabled()) {

			// it is only a substitute/manager or an authorization manager that can request roles, check if the user is a substitute, authorization manager or a manager
			if (managers.size() > 0 || !orgUnitService.getByAuthorizationManagerMatchingUser(user).isEmpty() || !orgUnitService.getByManagerMatchingUser(user).isEmpty()) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_REQUESTER));
			}
		}

		// flag user as manager if that is the case
		if (userService.isManager(user)) {
			isManager = true;
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_MANAGER));

			// Add directly managed OUs
			orgUnitService.getByManagerMatchingUser(user).stream()
				.map(OrgUnit::getUuid)
				.forEach(managerOuUuids::add);
		}

		if (isManager || isSubstitute || isDelegate) {
			// manager-like users get full access to the manager section, constrained by their Orgunits
			permissionService.addLimitedManagerAccess(user.getUuid(), managerOuUuids);
		}

		return managers;
	}

	private void handleRequestApproveRoles (boolean managersNotEmpty, User user,Set<SamlGrantedAuthority> authorities ) {
		// check if the request/approve feature is enabled
		if (settingsService.isRequestApproveEnabled()
				&& (managersNotEmpty
					|| !orgUnitService.getByAuthorizationManagerMatchingUser(user).isEmpty()
					|| !orgUnitService.getByManagerMatchingUser(user).isEmpty())) {
				authorities.add(new SamlGrantedAuthority(Constants.ROLE_REQUESTER));
			}

	}

	private void handleHierachialRoles(Set<String> roles, Set<SamlGrantedAuthority> grantedAuthorities) {
		boolean admin =roles.contains(Constants.ROLE_ADMINISTRATOR_ID);
		boolean globalAssigner = roles.contains(Constants.ROLE_GLOBAL_ASSIGNER_ID);
		boolean userAssigner = roles.contains(Constants.ROLE_USER_ASSIGNER_ID);
		boolean ouAssigner = roles.contains(Constants.ROLE_OU_ASSIGNER_ID);
		boolean readAccess = roles.contains(Constants.ROLE_READ_ACCESS_ID);

		Set<String> authorities = new HashSet<>();

		if (admin) {
			authorities.add(Constants.ROLE_ADMINISTRATOR);
			authorities.add(Constants.ROLE_REPORT_ACCESS);
			authorities.add(Constants.ROLE_KLE_ADMINISTRATOR);
			setNotifications();
		}
		if (globalAssigner || admin) {
			authorities.add(Constants.ROLE_AUDITLOG);
		}
		if (userAssigner || globalAssigner || admin) {
			authorities.add(Constants.ROLE_USER_ASSIGNER);
		}
		if (ouAssigner || globalAssigner || admin) {
			authorities.add(Constants.ROLE_OU_ASSIGNER);
		}

		if(admin|| globalAssigner || userAssigner || ouAssigner || readAccess) {
			authorities.add(Constants.ROLE_READ_ACCESS);
		}

		grantedAuthorities.addAll(authorities.stream()
				.map(SamlGrantedAuthority::new)
				.toList());
	}

	private void handleNonHierachialRoles(Set<String> roles, Set<SamlGrantedAuthority> authorities, User user) {
		if (roles.contains(Constants.ROLE_KLE_ADMINISTRATOR_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_KLE_ADMINISTRATOR));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
			permissionService.addRightsReadAccess(user.getUuid());
		}

		if (roles.contains(Constants.ROLE_ATTESTATION_ADMINISTRATOR_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_ATTESTATION_ADMINISTRATOR));
			permissionService.addFullAccess(Section.ATTESTATION, user.getUuid());
		}

		if (roles.contains(Constants.ROLE_REQUESTAUTHORIZED)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_REQUESTAUTHORIZED));
		}

		if (roles.contains(Constants.ROLE_REPORT_ACCESS_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_REPORT_ACCESS));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
			permissionService.addRightsReadAccess(user.getUuid());
			permissionService.addFullAccess(Section.REPORT, user.getUuid());
		}

		// Users without report access but with assigned Reports templates
		if (!roles.contains(Constants.ROLE_REPORT_ACCESS_ID) && !roles.contains(Constants.ROLE_ADMINISTRATOR) && !reportTemplateService.getByUser(user).isEmpty()) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_TEMPLATE_ACCESS));
			permissionService.updateConstraintFor( Section.REPORT,Permission.READ, user.getUuid(), new PermissionConstraint(null, null));
		}

		// check if user is it system responsible
		if (!itSystemService.findByAttestationResponsible(user).isEmpty()) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_IT_SYSTEM_RESPONSIBLE));
		}

	}
}
