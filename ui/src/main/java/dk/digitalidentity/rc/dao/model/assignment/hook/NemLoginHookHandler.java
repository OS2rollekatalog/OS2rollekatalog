package dk.digitalidentity.rc.dao.model.assignment.hook;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class NemLoginHookHandler implements AssignmentHookHandler {
	private final NemLoginService nemLoginService;
	private final UserRoleService userRoleService;
	private final RoleCatalogueConfiguration configuration;
	private final UserService userService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		if (!configuration.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}
		
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole != null) {
			if (Objects.equals(userRole.getItSystem().getIdentifier(), "NemLogin")) {
				User user = userService.getByUuid(hookEvent.getUserUuid());
				if (user != null) {
					nemLoginService.addUserToQueue(user);
				}
			}
		}
	}
}
