package dk.digitalidentity.rc.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.saml.model.TokenUser;

@Component
public class SecurityUtil {
	private static final String SYSTEM_USERID = "system";
	private static final String SYSTEM_USERNAME = "Systembruger";

	@Autowired
	private UserService userService;
	
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
	
	public static boolean isManagerWithoutReadAccess() {
		boolean manager = false;
		boolean readAccess = false;

		if (isLoggedIn()) {
			for (GrantedAuthority grantedAuthority : (SecurityContextHolder.getContext().getAuthentication()).getAuthorities()) {
				if (grantedAuthority.getAuthority().equals(Constants.ROLE_READ_ACCESS)) {
					readAccess = true;
				}
				else if (grantedAuthority.getAuthority().equals(Constants.ROLE_MANAGER) || grantedAuthority.getAuthority().equals(Constants.ROLE_SUBSTITUTE)) {
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
				}
				else if (grantedAuthority.getAuthority().equals(Constants.ROLE_MANAGER) || grantedAuthority.getAuthority().equals(Constants.ROLE_SUBSTITUTE)) {
					// manager does not count as a role as such ;)
				}
				else {
					// got a role different than requester, abort
					return false;
				}
			}
		}

		return requester;
	}

	private static boolean isLoggedIn() {
		if (SecurityContextHolder.getContext().getAuthentication() != null && SecurityContextHolder.getContext().getAuthentication().getDetails() != null && SecurityContextHolder.getContext().getAuthentication().getDetails() instanceof TokenUser) {
			return true;
		}

		return false;
	}

	public static String getUserId() {
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser) {
				TokenUser tokenUser = (TokenUser) o;
				
				return tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_USERID).toString();
			}
			
			return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		}
		
		return SYSTEM_USERID;
	}
	
	public static String getUserFullname() {
		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser) {
				TokenUser tokenUser = (TokenUser) o;
				
				return tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_NAME).toString();
			}
			
			return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		}

		return SYSTEM_USERNAME;
	}

	public List<User> getManagersBySubstitute() {
		List<User> result = new ArrayList<>();

		if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
			Object o = SecurityContextHolder.getContext().getAuthentication().getDetails();
			if (o instanceof TokenUser) {
				TokenUser tokenUser = (TokenUser) o;
				
				o = tokenUser.getAttributes().get(RolePostProcessor.ATTRIBUTE_SUBSTITUTE_FOR);
				if (o != null && o instanceof String[]) {
					String[] managerUuids = (String[]) o;
					
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
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		// apply to existing session
		if (authentication != null && authentication instanceof UsernamePasswordAuthenticationToken) {
			UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;
			
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
		}
		else { // create new session
			TokenUser tokenUser = TokenUser.builder()
					.cvr("N/A")
					.authorities(Collections.singletonList(new SimpleGrantedAuthority(Constants.ROLE_SYSTEM)))
					.username(SYSTEM_USERID)
					.attributes(new HashMap<String, Object>())
					.build();

			tokenUser.getAttributes().put(RolePostProcessor.ATTRIBUTE_USERID, SYSTEM_USERID);
			tokenUser.getAttributes().put(RolePostProcessor.ATTRIBUTE_NAME, SYSTEM_USERNAME);
			
			UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(SYSTEM_USERID, "N/A", tokenUser.getAuthorities());
			token.setDetails(tokenUser);
			SecurityContextHolder.getContext().setAuthentication(token);			
		}
	}
	
	public static void logoutSystemAccount() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		
		if (authentication != null && authentication instanceof UsernamePasswordAuthenticationToken) {
			UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) authentication;
			
			Object o = token.getDetails();
			if (o instanceof TokenUser) {
				TokenUser tokenUser = (TokenUser) o;
				
				// restore authorities from stored tokenUser
				UsernamePasswordAuthenticationToken newToken = new UsernamePasswordAuthenticationToken(token.getPrincipal(), "N/A", tokenUser.getAuthorities());
				newToken.setDetails(tokenUser);

				SecurityContextHolder.getContext().setAuthentication(token);
			}
			else {
				// fallback if no tokenUser is present
				SecurityContextHolder.getContext().setAuthentication(null);
			}
		}
	}

	public boolean hasRestrictedReadAccess() {
		String userId = getUserId();

		try {
			List<UserRole> roles = userService.getAllUserRoles(userId, Constants.ROLE_CATALOGUE_IDENTIFIER);
				
			for (UserRole role : roles) {
				for (SystemRoleAssignment assignment : role.getSystemRoleAssignments()) {
					if (assignment.getSystemRole().getIdentifier().equals(Constants.ROLE_READ_ACCESS_ID)) {
						return (assignment.getConstraintValues().size() > 0);
					}
				}
			}
		}
		catch (UserNotFoundException ex) {
			; // do nothing
		}

		return false;
	}
	
	public List<Long> getRestrictedReadAccessItSystems() {
		List<Long> result = new ArrayList<>();
		String userId = getUserId();

		try {
			List<UserRole> roles = userService.getAllUserRoles(userId, Constants.ROLE_CATALOGUE_IDENTIFIER);
				
			for (UserRole role : roles) {
				for (SystemRoleAssignment assignment : role.getSystemRoleAssignments()) {
					if (assignment.getSystemRole().getIdentifier().equals(Constants.ROLE_READ_ACCESS_ID)) {
						for (SystemRoleAssignmentConstraintValue constraintValue : assignment.getConstraintValues()) {
							if (constraintValue.getConstraintType().getEntityId().equals(Constants.ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
								String value = constraintValue.getConstraintValue();
								for (String token : value.split(",")) {
									result.add(Long.valueOf(token));
								}
							}
						}
					}
				}
			}
		}
		catch (UserNotFoundException ex) {
			; // do nothing
		}

		return result;
	}
}
