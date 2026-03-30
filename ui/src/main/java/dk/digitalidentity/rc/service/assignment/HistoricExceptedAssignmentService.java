package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.HistoricExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;
import dk.digitalidentity.rc.service.assignment.mapper.HistoricExceptedAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoricExceptedAssignmentService {

	private final HistoricExceptedAssignmentDao historicExceptedAssignmentDao;

	@Transactional
	public void createExceptedFromCurrentAssignments(Set<CurrentExceptedAssignment> currentExceptedAssignments) {
		Set<HistoricExceptedAssignment> historicAssignments = currentExceptedAssignments.stream()
			.map(HistoricExceptedAssignmentMapper::createFromCurrentExceptedAssignment)
			.collect(Collectors.toSet());

		historicExceptedAssignmentDao.saveAll(historicAssignments);
	}

	@Transactional
	public void updateValidToFor(Set<CurrentExceptedAssignment> currentExceptedAssignments, LocalDateTime validTo) {
		Set<String> recordHashes = currentExceptedAssignments.stream().map(CurrentExceptedAssignment::getRecordHash).collect(Collectors.toSet());
		Set<HistoricExceptedAssignment> historicAssignments = historicExceptedAssignmentDao.findAllByRecordHashIn(recordHashes);
		for (HistoricExceptedAssignment historicAssignment : historicAssignments) {
			historicAssignment.setValidTo(validTo);
		}
		historicExceptedAssignmentDao.saveAll(historicAssignments);
	}

	public Set<HistoricExceptedAssignment> getByUser(User user) {
		return historicExceptedAssignmentDao.findByExceptionUserUuid(user.getUuid());
	}



	/**
	 * Get all historic assignments that were active at the given date.
	 * An assignment is active if validFrom <= date (end of day) AND (validTo IS NULL OR validTo >= date (start of day))
	 */
	public List<HistoricExceptedAssignment> getActiveAtDate(LocalDate date) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicExceptedAssignmentDao.findActiveAtDate(startOfDay, endOfDay);
	}

	/**
	 * Get all historic assignments that were active at the given date and belong to the specified IT systems.
	 * An assignment is active if validFrom <= date (end of day) AND (validTo IS NULL OR validTo >= date (start of day))
	 */
	public List<HistoricExceptedAssignment> getActiveAtDateAndItSystems(LocalDate date, Collection<Long> itSystemIds) {
		if (itSystemIds == null || itSystemIds.isEmpty()) {
			return getActiveAtDate(date);
		}
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicExceptedAssignmentDao.findActiveAtDateAndItSystemIdIn(startOfDay, endOfDay, itSystemIds);
	}
}
