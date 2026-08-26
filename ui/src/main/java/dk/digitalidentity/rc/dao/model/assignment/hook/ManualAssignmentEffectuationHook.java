package dk.digitalidentity.rc.dao.model.assignment.hook;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.service.ManualAssignmentEffectuationService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers pending manual-effectuation rows (ManualAssignmentEffectuationService) whenever the
 * calculated (CurrentAssignment/HistoricAssignment) pipeline publishes a HookEvent for a role on a
 * MANUAL it-system - covering every way the role could have been granted (direct, org-unit, title,
 * role-group) uniformly, since PublishAssignmentService only ever publishes the already-flattened,
 * per-(user, userRole) outcome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualAssignmentEffectuationHook implements AssignmentHookHandler {
	private final ManualAssignmentEffectuationService manualAssignmentEffectuationService;
	private final UserRoleService userRoleService;
	private final UserService userService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		UserRole userRole = userRoleService.getById(hookEvent.getUserRoleId());
		if (userRole == null) {
			return;
		}

		if (userRole.getItSystem().getSystemType() != ItSystemType.MANUAL || !userRole.getItSystem().isManualEffectuationEnabled()) {
			return;
		}

		User user = userService.getByUuid(hookEvent.getUserUuid());
		if (user == null) {
			return;
		}

		ManualAssignmentEffectuationOperation operation = hookEvent.getAction() == HookEvent.HookAction.ADD
			? ManualAssignmentEffectuationOperation.ASSIGN
			: ManualAssignmentEffectuationOperation.REMOVE;

		manualAssignmentEffectuationService.registerPendingEffectuation(user, userRole, operation);
	}
}
