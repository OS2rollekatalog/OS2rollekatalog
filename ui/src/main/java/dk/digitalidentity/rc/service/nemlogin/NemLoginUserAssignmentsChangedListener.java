package dk.digitalidentity.rc.service.nemlogin;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.event.UserAssignmentsChangedListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "rc.integrations.nemlogin.enabled", havingValue = "true")
public class NemLoginUserAssignmentsChangedListener implements UserAssignmentsChangedListener {

	private final NemLoginService nemLoginService;

	@Override
	public void onUserAssignmentsChanged(User user) {
		nemLoginService.syncUserRoleAssignments(user);
	}
}
