package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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

	public Set<CurrentExceptedAssignment> getExceptedAssignmentsForUser(User user) {
		return currentExceptedAssignmentDao.findAllByExceptionUserUuid(user.getUuid());
	}

	@Transactional
	public List<CurrentExceptedAssignment> saveAllForUser(User user, Set<CurrentExceptedAssignment> assignmentExceptions) {
		Set<String> recordHashes = assignmentExceptions.stream().map(CurrentExceptedAssignment::getRecordHash).collect(Collectors.toSet());

		// find existing for user
		Set<CurrentExceptedAssignment> existing = getExceptedAssignmentsForUser(user);
		Set<String> existingRecordHashes = existing.stream().map(CurrentExceptedAssignment::getRecordHash).collect(Collectors.toSet());

		// delete all for this user not matching those we want to save
		Set<CurrentExceptedAssignment> toDelete = existing.stream()
			.filter(e -> !recordHashes.contains(e.getRecordHash()))
			.collect(Collectors.toSet());

		if (toDelete.size() > 0) {
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

		if (toCreate.size() > 0) {
			// newly created assignments also get a corresponding historic assignment, with the same recordhash
			historicExceptedAssignmentService.createExceptedFromCurrentAssignments(toCreate);
	
			return currentExceptedAssignmentDao.saveAll(toCreate);
		}
		
		return Collections.emptyList();
	}
}
