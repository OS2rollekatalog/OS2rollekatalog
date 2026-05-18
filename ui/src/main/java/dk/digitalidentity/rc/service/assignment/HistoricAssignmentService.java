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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		historicAssignmentDao.updateValidToByRecordHashIn(recordHashes, validTo);
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

	/**
	 * Lightweight projection returning [userUuid, itSystemName, userRoleId] — used for weight pre-computation.
	 * Returns plain Object arrays, not entity objects.
	 */
	public List<Object[]> getWeightTriples(LocalDate date) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.findWeightTriples(startOfDay, endOfDay);
	}

	public List<Object[]> getWeightTriplesForItSystems(LocalDate date, Collection<Long> itSystemIds) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.findWeightTriplesForItSystems(startOfDay, endOfDay, itSystemIds);
	}

	/**
	 * Pre-loads all constraints for assignments active at the given date as a map.
	 * Returns Map&lt;assignmentId, List&lt;[constraintTypeName, value]&gt;&gt;.
	 * Use this before streaming to avoid per-entity lazy loading (N+1).
	 */
	public Map<Long, List<Object[]>> getConstraintsForDate(LocalDate date) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.findConstraintProjectionsForDate(startOfDay, endOfDay)
			.stream()
			.collect(Collectors.groupingBy(row -> (Long) row[0]));
	}

	public Map<Long, List<Object[]>> getConstraintsForDateAndItSystems(LocalDate date, Collection<Long> itSystemIds) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.findConstraintProjectionsForDateAndItSystems(startOfDay, endOfDay, itSystemIds)
			.stream()
			.collect(Collectors.groupingBy(row -> (Long) row[0]));
	}

	/**
	 * Streams assignments using a server-side cursor.
	 * Caller must be @Transactional — close the returned stream with try-with-resources.
	 */
	public Stream<HistoricAssignment> streamActiveAtDate(LocalDate date) {
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.streamActiveAtDate(startOfDay, endOfDay);
	}

	public Stream<HistoricAssignment> streamActiveAtDateAndItSystems(LocalDate date, Collection<Long> itSystemIds) {
		if (itSystemIds == null || itSystemIds.isEmpty()) {
			return streamActiveAtDate(date);
		}
		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime endOfDay = date.atTime(23, 59, 59, 999999999);
		return historicAssignmentDao.streamActiveAtDateAndItSystemIdIn(startOfDay, endOfDay, itSystemIds);
	}
}
