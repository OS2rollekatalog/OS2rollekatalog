package dk.digitalidentity.rc.dao.model.assignment.hook;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.UserRoleService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ActiveDirectoryHookHandler implements AssignmentHookHandler {
	private final PendingADUpdateService pendingADUpdateService;
	private final UserRoleService userRoleService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole != null) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}
}
