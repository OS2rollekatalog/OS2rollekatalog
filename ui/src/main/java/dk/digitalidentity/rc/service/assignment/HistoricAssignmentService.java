package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.service.assignment.mapper.HistoricAssignmentMapper;
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
public class HistoricAssignmentService {

	private final HistoricAssignmentDao historicAssignmentDao;

	@Transactional
	public void createFromCurrentAssignments(Set<CurrentAssignment> currentAssignments) {
		Set<HistoricAssignment> historicAssignments = currentAssignments.stream()
			.map(HistoricAssignmentMapper::createFromCurrentAssignment)
			.collect(Collectors.toSet());

		historicAssignmentDao.saveAll(historicAssignments);
	}

	@Transactional
	public void updateValidToFor(Set<CurrentAssignment> currentAssignments, LocalDateTime validTo) {
		Set<String> recordHashes = currentAssignments.stream().map(CurrentAssignment::getRecordHash).collect(Collectors.toSet());
		Set<HistoricAssignment> historicAssignments = historicAssignmentDao.findAllByRecordHashIn(recordHashes);
		for (HistoricAssignment historicAssignment : historicAssignments) {
			historicAssignment.setValidTo(validTo);
		}
		historicAssignmentDao.saveAll(historicAssignments);
	}

	public Set<HistoricAssignment> getByUser(User user) {
		return historicAssignmentDao.findByUserUuid(user.getUuid());
	}



	/**
	 * Get all historic assignments that were active at the given date.
	 * An assignment is active if validFrom <= date (end of day) AND (validTo IS NULL OR validTo >= date (start of day))
	 */
	public List<HistoricAssignment> getActiveAtDate(LocalDate date) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.findActiveAtDate(startOfDay, endOfDay);
	}

	/**
	 * Get all historic assignments that were active at the given date and belong to the specified IT systems.
	 * An assignment is active if validFrom <= date (end of day) AND (validTo IS NULL OR validTo >= date (start of day))
	 */
	public List<HistoricAssignment> getActiveAtDateAndItSystems(LocalDate date, Collection<Long> itSystemIds) {
		if (itSystemIds == null || itSystemIds.isEmpty()) {
			return getActiveAtDate(date);
		}
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.findActiveAtDateAndItSystemIdIn(startOfDay, endOfDay, itSystemIds);
	}
}
