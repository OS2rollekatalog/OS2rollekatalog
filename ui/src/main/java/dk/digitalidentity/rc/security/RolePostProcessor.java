package dk.digitalidentity.rc.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.saml.extension.SamlLoginPostProcessor;
import dk.digitalidentity.saml.model.TokenUser;

@Component
@Transactional
public class RolePostProcessor implements SamlLoginPostProcessor {
	public static final String ATTRIBUTE_USERID = "ATTRIBUTE_USERID";
	public static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
	public static final String ATTRIBUTE_SUBSTITUTE_FOR = "ATTRIBUTE_SUBSTITUTE_FOR";

	@Autowired
	private UserService userService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private ItSystemService itSystemService;

	@Override
	public void process(TokenUser tokenUser) {
		String principal = tokenUser.getUsername();

		User user = userService.getByUserId(principal);
		if (user == null) {
			throw new UsernameNotFoundException("Brugeren " + principal + " er ikke kendt af rollekataloget!");
		}
		
		auditLogger.log(user, EventType.LOGIN_LOCAL);

		tokenUser.getAttributes().put(ATTRIBUTE_USERID, user.getUserId());
		tokenUser.getAttributes().put(ATTRIBUTE_NAME, user.getName());

		Set<String> roles = new HashSet<>();

		List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);

		List<UserRole> userRoles = userService.getAllUserRoles(user, itSystems);
		if (userRoles != null) {
			for (UserRole role : userRoles) {
				for (SystemRoleAssignment roleAssignment : role.getSystemRoleAssignments()) {
					roles.add(roleAssignment.getSystemRole().getIdentifier());
				}
			}
		}

		Set<GrantedAuthority> authorities = new HashSet<>();

		// if the request/approve feature is enabled, all users gets the requester role
		if (settingsService.isRequestApproveEnabled()) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_REQUESTER));
		}

		// flag user as manager if that is the case
		if (userService.isManager(user)) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_MANAGER));
		}

		// if any manager has flagged this user as a substitute, add the substitute role and keep track of the list of managers
		List<User> managers = userService.getSubstitutesManager(user);
		if (managers.size() > 0) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_SUBSTITUTE));
			tokenUser.getAttributes().put(ATTRIBUTE_SUBSTITUTE_FOR, managers.stream()
					.map(m -> m.getUuid())
					.collect(Collectors.toList())
					.toArray(new String[0]));
		}

		// hierarchy of roles
		if (roles.contains(Constants.ROLE_ADMINISTRATOR_ID)) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_ADMINISTRATOR));
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_ASSIGNER));
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_READ_ACCESS));
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_KLE_ADMINISTRATOR));
		}
		else if (roles.contains(Constants.ROLE_ASSIGNER_ID)) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_ASSIGNER));
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}
		else if (roles.contains(Constants.ROLE_READ_ACCESS_ID)) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}

		// roles outside hierarchy
		if (roles.contains(Constants.ROLE_KLE_ADMINISTRATOR_ID)) {
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_KLE_ADMINISTRATOR));
			authorities.add(new SimpleGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}
		
		tokenUser.setAuthorities(authorities);
	}
}
