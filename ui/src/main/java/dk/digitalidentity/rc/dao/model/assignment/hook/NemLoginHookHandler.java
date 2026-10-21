package dk.digitalidentity.rc.dao.model.assignment.hook;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class NemLoginHookHandler implements AssignmentHookHandler {
	private final NemLoginService nemLoginService;
	private final RoleCatalogueConfiguration configuration;
	private final UserService userService;
	private final ItSystemService itSystemService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		if (!configuration.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}
		
		ItSystem itSystem = itSystemService.getById(hookEvent.getItSystemId());
		if (itSystem != null && Objects.equals(itSystem.getIdentifier(), "NemLogin")) {
			User user = userService.getByUuid(hookEvent.getUserUuid());
			if (user != null) {
				nemLoginService.addUserToQueue(user);
			}
		}
	}
}
