package dk.digitalidentity.rc.dao.model.assignment.hook;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.cics.KspCicsService;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class KspCicsHookHandler implements AssignmentHookHandler {
	private final KspCicsService kspCicsService;
	private final UserService userService;
	private final ItSystemService itSystemService;

	@Override
	public void handleEvent(HookEvent hookEvent) {
		ItSystem itSystem = itSystemService.getById(hookEvent.getItSystemId());
		if (itSystem != null && Objects.equals(itSystem.getIdentifier(), "KSPCICS")) {
			User user = userService.getByUuid(hookEvent.getUserUuid());
			if (user != null) {
				kspCicsService.addUserToQueue(user);
			}
		}
	}
}
