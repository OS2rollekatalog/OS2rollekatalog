package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.event.UserAssignmentsChangedListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reacts to assignment changes by marking the user as pending for the next manual-it-system
 * notification flush ({@code ManualRolesService.processPendingUsers()}). Marking is cheap; the
 * actual diff and digest mails happen in the batched flush so several changed users on the same
 * it-system are collapsed into a single mail.
 *
 * Only fires on instances where the scheduled jobs are enabled, since
 * {@code UserAssignmentsChangedEventHandler} only activates the queue when {@code scheduled.enabled} is set.
 */
@RequiredArgsConstructor
@Component
public class ManualRolesUserAssignmentsChangedListener implements UserAssignmentsChangedListener {

	private final ManualRolesService manualRolesService;

	@Override
	public void onUserAssignmentsChanged(User user) {
		manualRolesService.markUserPending(user);
	}
}
