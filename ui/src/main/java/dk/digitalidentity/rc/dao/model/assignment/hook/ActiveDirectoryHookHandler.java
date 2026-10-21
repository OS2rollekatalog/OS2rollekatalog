package dk.digitalidentity.rc.dao.model.assignment.hook;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.UserRoleService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ActiveDirectoryHookHandler implements AssignmentHookHandler {
	private final PendingADUpdateService pendingADUpdateService;
	private final UserRoleService userRoleService;
	private final ItSystemService itSystemService;

	@Override
	@Transactional
	public void handleEvent(HookEvent hookEvent) {
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole != null) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
		else {
			ItSystem itSystem = itSystemService.getById(hookEvent.getItSystemId());
			if (itSystem != null) {
				pendingADUpdateService.addItSystemToQueue(itSystem);
			}
		}
	}
}
