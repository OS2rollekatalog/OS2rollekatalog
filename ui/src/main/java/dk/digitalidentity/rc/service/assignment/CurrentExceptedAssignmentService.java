package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.assignment.CurrentExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CurrentExceptedAssignmentService {

	private final CurrentExceptedAssignmentDao currentExceptedAssignmentDao;
	private final HistoricExceptedAssignmentService historicExceptedAssignmentService;

	/**
	 * Batch-variant af saveAllForUser — udfører alle DB-operationer for alle brugere på én gang.
	 */
	@Transactional
	public void saveAllForUsers(Map<User, Set<CurrentExceptedAssignment>> exceptionsByUser) {
		if (exceptionsByUser.isEmpty()) {
			return;
		}

		Set<String> userUuids = exceptionsByUser.keySet().stream().map(User::getUuid).collect(Collectors.toSet());

		// 1 SELECT for alle brugere
		Map<String, Map<String, CurrentExceptedAssignment>> existingByUserUuidAndHash =
			currentExceptedAssignmentDao.findAllByExceptionUserUuidIn(userUuids).stream()
				.collect(Collectors.groupingBy(
					CurrentExceptedAssignment::getExceptionUserUuid,
					Collectors.toMap(CurrentExceptedAssignment::getRecordHash, e -> e, (e, ignored) -> e)
				));

		Set<CurrentExceptedAssignment> allToDelete = new HashSet<>();
		Set<CurrentExceptedAssignment> allToCreate = new HashSet<>();

		for (Map.Entry<User, Set<CurrentExceptedAssignment>> entry : exceptionsByUser.entrySet()) {
			Map<String, CurrentExceptedAssignment> existingByHash = existingByUserUuidAndHash.getOrDefault(entry.getKey().getUuid(), Map.of());
			Set<String> newHashes = entry.getValue().stream().map(CurrentExceptedAssignment::getRecordHash).collect(Collectors.toSet());

			existingByHash.entrySet().stream()
				.filter(e -> !newHashes.contains(e.getKey()))
				.map(Map.Entry::getValue)
				.forEach(allToDelete::add);

			entry.getValue().stream()
				.filter(e -> !existingByHash.containsKey(e.getRecordHash()))
				.forEach(allToCreate::add);
		}

		if (!allToDelete.isEmpty()) {
			historicExceptedAssignmentService.updateValidToFor(allToDelete, LocalDateTime.now());
			currentExceptedAssignmentDao.deleteAll(allToDelete);
		}

		if (!allToCreate.isEmpty()) {
			historicExceptedAssignmentService.createExceptedFromCurrentAssignments(allToCreate);
			currentExceptedAssignmentDao.saveAll(allToCreate);
		}
	}

	public Set<CurrentExceptedAssignment> getExceptedAssignmentsForUser(User user) {
		return currentExceptedAssignmentDao.findAllByExceptionUserUuid(user.getUuid());
	}

	@Transactional
	public boolean saveAllForUser(User user, Set<CurrentExceptedAssignment> assignmentExceptions) {
		Set<String> recordHashes = assignmentExceptions.stream().map(CurrentExceptedAssignment::getRecordHash).collect(Collectors.toSet());

		// find existing for user
		Set<CurrentExceptedAssignment> existing = getExceptedAssignmentsForUser(user);
		Set<String> existingRecordHashes = existing.stream().map(CurrentExceptedAssignment::getRecordHash).collect(Collectors.toSet());

		// delete all for this user not matching those we want to save
		Set<CurrentExceptedAssignment> toDelete = existing.stream()
			.filter(e -> !recordHashes.contains(e.getRecordHash()))
			.collect(Collectors.toSet());

		if (!toDelete.isEmpty()) {
			// Deleted excepted assignments - update their corresponding historic assignments validTo
			historicExceptedAssignmentService.updateValidToFor(toDelete, LocalDateTime.now());

			// delete from current table
			currentExceptedAssignmentDao.deleteAll(toDelete);
		}

		// save all those that do not already exists by recordHash.
		// It is not nessecary to update those that already exists.
		Set<CurrentExceptedAssignment> toCreate = assignmentExceptions.stream()
			.filter(e -> !existingRecordHashes.contains(e.getRecordHash()))
			.collect(Collectors.toSet());

		if (!toCreate.isEmpty()) {
			// newly created assignments also get a corresponding historic assignment, with the same recordhash
			historicExceptedAssignmentService.createExceptedFromCurrentAssignments(toCreate);

			currentExceptedAssignmentDao.saveAll(toCreate);
		}

		return !toDelete.isEmpty() || !toCreate.isEmpty();
	}
}
