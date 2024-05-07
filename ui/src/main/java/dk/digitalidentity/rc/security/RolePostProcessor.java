package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.ReportTemplateService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.SamlLoginPostProcessor;
import dk.digitalidentity.samlmodule.model.TokenUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional
public class RolePostProcessor implements SamlLoginPostProcessor {
	public static final String ATTRIBUTE_USERID = "ATTRIBUTE_USERID";
	public static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
	public static final String ATTRIBUTE_SUBSTITUTE_FOR = "ATTRIBUTE_SUBSTITUTE_FOR";
	public static final String ATTRIBUTE_CLIENT = "ATTRIBUTE_CLIENT";

	@Autowired
	private UserService userService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private ReportTemplateService reportTemplateService;
	
	@Autowired 
	private NotificationService notificationService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private RequestApproveService requestApproveService;

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
		
		Set<SamlGrantedAuthority> authorities = new HashSet<>();

		// if any manager has flagged this user as a substitute, add the substitute role and keep track of the list of managers
		List<User> managers = userService.getSubstitutesManager(user);
		if (managers.size() > 0) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_SUBSTITUTE));
			tokenUser.getAttributes().put(ATTRIBUTE_SUBSTITUTE_FOR, managers.stream()
					.map(m -> m.getUuid())
					.collect(Collectors.toList())
					.toArray(new String[0]));
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
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_MANAGER));
		}

		// hierarchy of roles
		if (roles.contains(Constants.ROLE_ADMINISTRATOR_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_ADMINISTRATOR));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_ASSIGNER));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_REPORT_ACCESS));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_KLE_ADMINISTRATOR));
			setNotifications();
		}
		else if (roles.contains(Constants.ROLE_ASSIGNER_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_ASSIGNER));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}
		else if (roles.contains(Constants.ROLE_READ_ACCESS_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}

		// roles outside hierarchy
		if (roles.contains(Constants.ROLE_KLE_ADMINISTRATOR_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_KLE_ADMINISTRATOR));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}

		if (roles.contains(Constants.ROLE_ATTESTATION_ADMINISTRATOR_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_ATTESTATION_ADMINISTRATOR));
		}

		if (roles.contains(Constants.ROLE_REPORT_ACCESS_ID)) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_REPORT_ACCESS));
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_READ_ACCESS));
		}

		// Users without roles but with assigned Reports templates
		if (roles.size() == 0 && reportTemplateService.getByUser(user).size() > 0) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_TEMPLATE_ACCESS));
		}

		// check if user is it system responsible
		if (!itSystemService.findByAttestationResponsible(user).isEmpty()) {
			authorities.add(new SamlGrantedAuthority(Constants.ROLE_IT_SYSTEM_RESPONSIBLE));
		}
		
		tokenUser.setAuthorities(authorities);
		
		// set number of requests on session if user is role assigner
		if (authorities.stream().anyMatch(a -> a.getAuthority().equals(Constants.ROLE_ASSIGNER))) {
			setRequests();
		}
	}
	
	private void setNotifications() {
		HttpServletRequest request = getRequest();
		
		if (request != null) {
			long count = notificationService.countActive();
			if (count > 0) {
				request.getSession().setAttribute(SessionConstants.SESSION_NOTIFICATION_COUNT, count);
			}

			count = requestApproveService.count();
			if (count > 0) {
				request.getSession().setAttribute(SessionConstants.SESSION_REQUEST_COUNT, count);
			}
		}
	}
	
	private void setRequests() {
		HttpServletRequest request = getRequest();
		
		if (request != null) {
			long count = requestApproveService.getPendingRequestsAuthorizationManager().size();
			request.getSession().setAttribute(SessionConstants.SESSION_REQUEST_COUNT, count);
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
}
