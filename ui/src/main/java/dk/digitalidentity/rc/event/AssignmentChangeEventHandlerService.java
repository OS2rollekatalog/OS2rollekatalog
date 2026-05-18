package dk.digitalidentity.rc.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
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

	// InvalidDataAccessApiUsageException is retried because concurrent deletes (e.g. IT-system deletion)
	// can cause Hibernate to reference entities that were removed mid-transaction, which is transient
	@Retryable(retryFor = {CannotAcquireLockException.class, InvalidDataAccessApiUsageException.class}, maxAttempts = 5, backoff = @Backoff(delay = 500, multiplier = 2))
	@Transactional
	public List<User> updateUsers(final Set<String> userUuids) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final List<User> users = userService.getAllByUuidIn(userUuids);

		final Map<User, Set<CurrentAssignment>> assignmentsByUser = new HashMap<>();
		final Map<User, ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>>> calculatedByUser = new HashMap<>();

		for (User user : users) {
			final ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> assignments = currentAssignmentCalculator.calculateAllAssignmentsForUser(user);
			assignmentsByUser.put(user, assignments.getLeft());
			calculatedByUser.put(user, assignments);
		}

		// upsert by recordHash (batch)
		final Set<User> assignmentChangedUsers = currentAssignmentService.saveAllForUsers(assignmentsByUser);

		// save exceptions to inherited ou assignments
		final Set<User> exceptionChangedUsers = new HashSet<>();
		for (User user : users) {
			boolean exceptionsChanged = currentExceptedAssignmentService.saveAllForUser(user, calculatedByUser.get(user).getRight());
			if (exceptionsChanged) {
				exceptionChangedUsers.add(user);
			}
		}

		return users.stream()
			.filter(u -> assignmentChangedUsers.contains(u) || exceptionChangedUsers.contains(u))
			.toList();
	}

}
