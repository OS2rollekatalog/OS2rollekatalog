package dk.digitalidentity.rc.dao.model.assignment.hook;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ManualRolesService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ManualItSystemHookHandler implements AssignmentHookHandler {
	private final ManualRolesService manualRolesService;
	private final UserRoleService userRoleService;
	private final UserService userService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole != null) {
			if (Objects.equals(userRole.getItSystem().getSystemType(), ItSystemType.MANUAL)) {
				User user = userService.getByUuid(hookEvent.getUserUuid());
				if (user != null) {
					manualRolesService.markUserPending(user);
				}
			}
		}
	}
}
