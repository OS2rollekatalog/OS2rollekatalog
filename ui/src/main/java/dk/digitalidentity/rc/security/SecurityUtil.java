package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class SecurityUtil {
	private static final String SYSTEM_USERID = "system";
	private static final String SYSTEM_USERNAME = "Systembruger";

	private final UserService userService;

	public static List<String> getRoles() {
		List<String> roles = new ArrayList<>();

		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				roles.add(grantedAuthority.getAuthority());
			}
		}

		return roles;
	}

	public static boolean hasRole(String role) {
		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(role)) {
					return true;
				}
			}
		}

		return false;
	}

	@Deprecated(forRemoval = true)
	// Usage should be replaced with an section-specific method ( hasPermission with READ permission)
	public static boolean doesNotHaveReadAccess() {
		boolean readAccess = false;

		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(Constants.ROLE_READ_ACCESS)) {
					readAccess = true;
				}
			}
		}

		return !readAccess;
	}

	public static boolean isManagerWithoutReadAccess() {
		boolean manager = false;
		boolean readAccess = false;

		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(Constants.ROLE_READ_ACCESS)) {
					readAccess = true;
				} else if (grantedAuthority.getAuthority().equals(Constants.ROLE_MANAGER) || grantedAuthority.getAuthority().equals(Constants.ROLE_SUBSTITUTE)) {
					manager = true;
				}
			}
		}

		return (manager && !readAccess);
	}

	public static boolean isRequesterAndOnlyRequester() {
		boolean requester = false;

		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(Constants.ROLE_REQUESTER)) {
					requester = true;
				} else if (grantedAuthority.getAuthority().equals(Constants.ROLE_MANAGER) || grantedAuthority.getAuthority().equals(Constants.ROLE_SUBSTITUTE)) {
					// manager does not count as a role as such ;)
				} else {
					// got a role different than requester, abort
					return false;
				}
			}
		}

		return requester;
	}

	public static boolean isSystemResponsible() {
		boolean systemResponsible = false;

		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(Constants.ROLE_IT_SYSTEM_RESPONSIBLE)) {
					systemResponsible = true;
				}
			}
		}

		return systemResponsible;
	}

	/**
	 * Evaluates if the currently logged in user has the direct Administrator role, regardless of Permissions
	 * @return true if user is logged in and has the role ROLE_ADMINISTRATOR as authority. False otherwise
	 */
	public static boolean hasDirectAdminRole() {
		if (isLoggedIn()) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null) {
				return false;
			}
			for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
				if (Constants.ROLE_ADMINISTRATOR.equals(grantedAuthority.getAuthority())) {
					return true;
				}
			}
		}
		return false;
	}

	@Deprecated(forRemoval = true)
	// Usage should be replaced with an section-specific method ( hasPermission with READ permission)
	public static boolean isAttestationAdminOrAdmin() {
		return isLoggedIn() &&
				SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.anyMatch(a -> a.equals(Constants.ROLE_ATTESTATION_ADMINISTRATOR)
								|| a.equals(Constants.ROLE_ADMINISTRATOR));
	}

	private static boolean isLoggedIn() {
		return SecurityContextHolder.getContext().getAuthentication() != null
				&& SecurityContextHolder.getContext().getAuthentication().getDetails() != null
				&& SecurityContextHolder.getContext().getAuthentication().getDetails() instanceof TokenUser;
	}

	public static String getUserId() {
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser tokenUser) {

				return tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_USERID).toString();
			}

			return extractPrincipal();
		}

		return SYSTEM_USERID;
	}

	public static String getUserUuid() {
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser tokenUser) {
				Object uuid = tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_USER_UUID);
				if (uuid == null) {
					return SYSTEM_USERID;
				}
				return uuid.toString();
			}

			return extractPrincipal();
		}

		return SYSTEM_USERID;
	}

	private static String extractPrincipal() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof Saml2AuthenticatedPrincipal) {
			return ((Saml2AuthenticatedPrincipal) principal).getName();
		}
		return (String) principal;
	}

	public static String getUserFullname() {
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser tokenUser) {

				return tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_NAME).toString();
			}

			return extractPrincipal();
		}

		return SYSTEM_USERNAME;
	}

	public static Client getClient() {
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser tokenUser) {

				return (Client) tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_CLIENT);
			}
		}

		return null;
	}

	public List<User> getManagersBySubstitute() {
		List<User> result = new ArrayList<>();

		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser tokenUser) {
				o = tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_SUBSTITUTE_FOR);
				if (o instanceof String[] managerUuids) {
					for (String managerUuid : managerUuids) {
						User manager = userService.getByUuid(managerUuid);
						if (manager != null) {
							result.add(manager);
						}
					}
				}
			}
		}

		return result;
	}

	public static void loginSystemAccount() {
		loginSystemAccount(new ArrayList<>(), null);
	}

	public static void loginSystemAccount(ArrayList<SamlGrantedAuthority> authorities, Client client) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// apply to existing session
		if (authentication instanceof UsernamePasswordAuthenticationToken token) {

			boolean hasSystemRole = false;
			for (GrantedAuthority authority : token.getAuthorities()) {
				if (authority.getAuthority().equals(Constants.ROLE_SYSTEM)) {
					hasSystemRole = true;
				}
			}

			if (!hasSystemRole) {
				List<GrantedAuthority> newAuthorities = new ArrayList<>(token.getAuthorities());
				newAuthorities.add(new SimpleGrantedAuthority(Constants.ROLE_SYSTEM));

				UsernamePasswordAuthenticationToken newToken = new UsernamePasswordAuthenticationToken(token.getPrincipal(), "N/A", newAuthorities);
				newToken.setDetails(token.getDetails()); // details contains the old credentials, which we will use to restore old authorities later

				SecurityContextHolder.getContext().setAuthentication(newToken);
			}
		} else { // create new session
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
			TokenUser tokenUser = TokenUser.builder()
					.cvr("N/A")
					.authorities(authorities)
					.username(SYSTEM_USERID)
					.attributes(new HashMap<>())
					.build();

			tokenUser.getAttributes().put(RolePostProcessor.ATTRIBUTE_USERID, SYSTEM_USERID);
			tokenUser.getAttributes().put(RolePostProcessor.ATTRIBUTE_NAME, SYSTEM_USERNAME);
			tokenUser.getAttributes().put(RolePostProcessor.ATTRIBUTE_CLIENT, client);

			UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(SYSTEM_USERID, "N/A", tokenUser.getAuthorities());
			token.setDetails(tokenUser);
			SecurityContextHolder.getContext().setAuthentication(token);
		}
	}

	public static void logoutSystemAccount() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication instanceof UsernamePasswordAuthenticationToken token) {
			Object o = token.getDetails();
			if (o instanceof TokenUser tokenUser) {

				// restore authorities from stored tokenUser
				UsernamePasswordAuthenticationToken newToken = new UsernamePasswordAuthenticationToken(token.getPrincipal(), "N/A", tokenUser.getAuthorities());
				newToken.setDetails(tokenUser);

				SecurityContextHolder.getContext().setAuthentication(token);
			} else {
				// fallback if no tokenUser is present
				SecurityContextHolder.getContext().setAuthentication(null);
			}
		}
	}


	/**
	 * Constructs a map of permissions for each permissionenabled section, based on the given set of role identifiers
	 *
	 * @param roles a set of identifiers for role catalogue roles
	 * @return a map of Permissions, grouped by the Sections they apply to
	 */
	public static Map<Section, Set<Permission>> getPermissionsForRoles(Set<String> roles) {
		EnumMap<Section, Set<Permission>> permissionMap = new EnumMap<>(Section.class);

		for (String role : roles) {
			switch (role) {
				case Constants.ROLE_ADMINISTRATOR_ID -> {
					for (Section section : Section.values()) {
						addAllPermissionsToMap(permissionMap, section, Arrays.stream(Permission.values()).toList());
					}
				}
				case Constants.ROLE_USER_ASSIGNER_ID -> {
					addPermissionsToMap(permissionMap, Section.USER, Permission.ASSIGN);
					addRightsReadPermissions(permissionMap);
				}
				case Constants.ROLE_OU_ASSIGNER_ID -> {
					addPermissionsToMap(permissionMap, Section.ORGUNIT, Permission.ASSIGN);
					addRightsReadPermissions(permissionMap);
				}
				case Constants.ROLE_GLOBAL_ASSIGNER_ID -> {
					addPermissionsToMap(permissionMap, Section.USER, Permission.ASSIGN);
					addPermissionsToMap(permissionMap, Section.ORGUNIT, Permission.ASSIGN);
					addRightsReadPermissions(permissionMap);
					addFullAuditlogAccess(permissionMap);
					addFullAdviseAccess(permissionMap);
				}
				case Constants.ROLE_READ_ACCESS_ID -> addRightsReadPermissions(permissionMap);
				case Constants.ROLE_REPORT_ACCESS_ID -> addFullReportAccess(permissionMap);
				case Constants.ROLE_ATTESTATION_ADMINISTRATOR_ID -> addFullAttestationAccess(permissionMap);

				case Constants.ROLE_USERROLE_READ_ID ->
						addPermissionsToMap(permissionMap, Section.USER_ROLE, Permission.READ);
				case Constants.ROLE_USERROLE_UPDATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.USER_ROLE, Set.of(Permission.UPDATE, Permission.READ));
				case Constants.ROLE_USERROLE_CREATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.USER_ROLE, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ));
				case Constants.ROLE_USERROLE_DELETE_ID ->
						addAllPermissionsToMap(permissionMap, Section.USER_ROLE, Set.of(Permission.DELETE, Permission.READ));
				case Constants.ROLE_ROLEGROUP_READ_ID ->
						addPermissionsToMap(permissionMap, Section.ROLE_GROUP, Permission.READ);
				case Constants.ROLE_ROLEGROUP_UPDATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.ROLE_GROUP, Set.of(Permission.UPDATE, Permission.READ));
				case Constants.ROLE_ROLEGROUP_CREATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.ROLE_GROUP, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ));
				case Constants.ROLE_ROLEGROUP_DELETE_ID ->
						addAllPermissionsToMap(permissionMap, Section.ROLE_GROUP, Set.of(Permission.DELETE, Permission.READ));
				case Constants.ROLE_ITSYSTEM_READ_ID ->
						addPermissionsToMap(permissionMap, Section.IT_SYSTEM, Permission.READ);
				case Constants.ROLE_ITSYSTEM_UPDATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.IT_SYSTEM, Set.of(Permission.UPDATE, Permission.READ));
				case Constants.ROLE_ITSYSTEM_CREATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.IT_SYSTEM, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ));
				case Constants.ROLE_ITSYSTEM_DELETE_ID ->
						addAllPermissionsToMap(permissionMap, Section.IT_SYSTEM, Set.of(Permission.DELETE, Permission.READ));
				case Constants.ROLE_OU_READ_ID -> addOUReadAccess(permissionMap);
				case Constants.ROLE_OU_UPDATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.ORGUNIT, Set.of(Permission.UPDATE, Permission.READ));
				case Constants.ROLE_USER_READ_ID -> addUserReadAccess(permissionMap);
				case Constants.ROLE_USER_UPDATE_ID ->
						addAllPermissionsToMap(permissionMap, Section.USER, Set.of(Permission.UPDATE, Permission.READ));
				case Constants.ROLE_LOG_READ_ID -> addFullAuditlogAccess(permissionMap);
				case Constants.ROLE_ADVISE_READ_ID -> addFullAdviseAccess(permissionMap);
				case Constants.ROLE_MANAGER_READ_ID ->
						addPermissionsToMap(permissionMap, Section.MANAGER, Permission.READ);
				case Constants.ROLE_MANAGER_UPDATE_ID -> addFullManagerAccess(permissionMap);
				case Constants.ROLE_CONFIG_READ_ID ->
						addAllPermissionsToMap(permissionMap, Section.CONFIG, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ, Permission.DELETE));
				default -> {
					// nothing happens for other roles.
					// They might conditionally add permissions in rolePostProcessor
				}
			}
		}
		return permissionMap;
	}

	private static void addFullAdviseAccess(Map<Section, Set<Permission>> permissionMap) {
		addAllPermissionsToMap(permissionMap, Section.ADVISE, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ, Permission.DELETE));
	}

	private static void addPermissionsToMap(Map<Section, Set<Permission>> permissionMap, Section entity, Permission permission) {
		Set<Permission> existingPermissions = permissionMap.getOrDefault(entity, new HashSet<>());
		existingPermissions.add(permission);
		permissionMap.put(entity, existingPermissions);
	}

	private static void addAllPermissionsToMap(Map<Section, Set<Permission>> permissionMap, Section entity, Collection<Permission> permissions) {
		for (Permission permission : permissions) {
			addPermissionsToMap(permissionMap, entity, permission);
		}
	}

	private static void addRightsReadPermissions(Map<Section, Set<Permission>> permissionMap) {
		for (Section section : List.of(
				Section.USER_ROLE,
				Section.ROLE_GROUP,
				Section.IT_SYSTEM,
				Section.USER,
				Section.ORGUNIT)) {
			addPermissionsToMap(permissionMap, section, Permission.READ);
		}
	}

	private static void addFullManagerAccess(Map<Section, Set<Permission>> permissionMap) {
		addAllPermissionsToMap(permissionMap, Section.MANAGER,  Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ, Permission.DELETE));
	}

	private static void addFullAuditlogAccess(Map<Section, Set<Permission>> permissionMap) {
		addAllPermissionsToMap(permissionMap, Section.LOG, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ, Permission.DELETE));
	}

	private static void addFullReportAccess(Map<Section, Set<Permission>> permissionMap) {
		addAllPermissionsToMap(permissionMap, Section.REPORT, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ, Permission.DELETE));
	}

	private static void addFullAttestationAccess(Map<Section, Set<Permission>> permissionMap) {
		addAllPermissionsToMap(permissionMap, Section.ATTESTATION, Set.of(Permission.UPDATE, Permission.CREATE, Permission.READ, Permission.DELETE));
	}

	private static void addUserReadAccess(Map<Section, Set<Permission>> permissionMap) {
		addPermissionsToMap(permissionMap, Section.USER, Permission.READ);
	}

	private static void addOUReadAccess(Map<Section, Set<Permission>> permissionMap) {
		addPermissionsToMap(permissionMap, Section.ORGUNIT, Permission.READ);
	}
}
