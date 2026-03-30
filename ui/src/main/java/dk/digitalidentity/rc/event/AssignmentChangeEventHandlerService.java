package dk.digitalidentity.rc.event;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.service.assignment.CurrentExceptedAssignmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class AssignmentChangeEventHandlerService {
	private final UserService userService;
	private final CurrentAssignmentCalculator currentAssignmentCalculator;
	private final CurrentAssignmentService currentAssignmentService;
	private final CurrentExceptedAssignmentService currentExceptedAssignmentService;

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public boolean updateUsers(final Set<String> userUuids) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final List<User> users = userService.getAllByUuidIn(userUuids);

		for (User user : users) {
			// calculate current assignments
			final var assignments = currentAssignmentCalculator.calculateAllAssignmentsForUser(user);

			// upsert by recordHash
			currentAssignmentService.saveAll(user, assignments.getLeft());

			// save exceptions to inherited ou assignments
			currentExceptedAssignmentService.saveAllForUser(user, assignments.getRight());
		}

		return true;
	}

	// old single-batch method - keeping it to ensure we CAN handle such events
	@Transactional
	public boolean updateUser(final String userUuid) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final User user = userService.getOptionalByUuid(userUuid).orElseThrow(() -> new RuntimeException("Unable to find user with uuid " + userUuid));

		// calculate current assignments
		final var assignments = currentAssignmentCalculator.calculateAllAssignmentsForUser(user);

		// upsert by recordHash
		currentAssignmentService.saveAll(user, assignments.getLeft());

		// save exceptions to inherited ou assignments
		currentExceptedAssignmentService.saveAllForUser(user, assignments.getRight());
		return true;
	}
}
