package dk.digitalidentity.rc.dao.model.assignment.hook;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.cics.KspCicsService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class KspCicsHookHandler implements AssignmentHookHandler {
	private final KspCicsService kspCicsService;
	private final UserService userService;
	private final UserRoleService userRoleService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole != null) {
			if (Objects.equals(userRole.getItSystem().getIdentifier(), "KSPCICS")) {
				User user = userService.getByUuid(hookEvent.getUserUuid());
				if (user != null) {
					kspCicsService.addUserToQueue(user);
				}
			}
		}
	}
}
