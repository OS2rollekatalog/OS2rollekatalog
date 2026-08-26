package dk.digitalidentity.rc.dao.model.assignment.hook;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.dmp.DMPService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class DmpHookHandler implements AssignmentHookHandler {
	private final DMPService dmpService;
	private final UserRoleService userRoleService;
	private final RoleCatalogueConfiguration configuration;
	private final UserService userService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		if (!configuration.getIntegrations().getDmp().isEnabled()) {
			return;
		}
		
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole != null) {
			if (Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
				User user = userService.getByUuid(hookEvent.getUserUuid());
				if (user != null) {
					dmpService.queueUser(user);
				}
			}
		}
	}
}
