package dk.digitalidentity.rc.test.integration.dao;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.CurrentExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;
import dk.digitalidentity.rc.service.assignment.AssignedWhenRepairService;
import dk.digitalidentity.rc.task.repair.AssignedWhenRepairJob;
import dk.digitalidentity.rc.task.repair.ExceptedAssignedWhenRepairJob;
import dk.digitalidentity.rc.task.repair.RepairJob;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The repair has to agree with the live mapper: both answer "when was this assigned", one for rows
 * written from now on and one for rows written before the column existed. So the mapper is the
 * oracle - let it stamp the rows, wipe what it wrote, run the repair, and the values have to come
 * back identical.
 * <p>
 * Two test methods rather than one per assertion: the fixture is a whole assignment graph behind a
 * full application context, so another method costs more than the coverage it adds.
 */
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("assigned_when repair")
class AssignedWhenRepairIntegrationTest extends BaseIntegrationTest {

	private final BasicTestDataFactory testDataFactory;
	private final CurrentAssignmentDao currentAssignmentDao;
	private final CurrentExceptedAssignmentDao currentExceptedAssignmentDao;
	private final HistoricAssignmentDao historicAssignmentDao;
	private final HistoricExceptedAssignmentDao historicExceptedAssignmentDao;
	private final AssignedWhenRepairService assignedWhenRepairService;
	private final AssignedWhenRepairJob assignedWhenRepairJob;
	private final ExceptedAssignedWhenRepairJob exceptedAssignedWhenRepairJob;

	@Test
	@DisplayName("Should restore exactly the dates the mapper wrote, and leave closed versions alone")
	void shouldRestoreTheDatesTheMapperWrote() {
		testDataFactory.createBasicTestData();
		flushAndClear();

		Map<Long, LocalDateTime> assignments = assignedWhenByCurrentAssignmentId();
		Map<Long, LocalDateTime> openHistoric = openHistoricAssignedWhenById();
		Map<Long, LocalDateTime> exceptions = assignedWhenByCurrentExceptionId();
		Map<Long, LocalDateTime> openHistoricExceptions = openHistoricExceptedAssignedWhenById();
		assertThat(assignments).as("the mapper stamped a date on the assignments up front").isNotEmpty();
		assertThat(openHistoric).as("the mapper carried the date over to the historic rows").isNotEmpty();
		assertThat(exceptions).as("the test data contains excepted assignments").isNotEmpty();
		assertThat(openHistoricExceptions).as("the exceptions reached the historic table").isNotEmpty();
		assertThat(sourceKinds()).as("all four source tables are exercised")
			.contains("DIRECT_ROLE", "DIRECT_ROLE_GROUP", "OU_ROLE", "OU_ROLE_GROUP");

		long closedId = addClosedVersionSharingHashWithAnOpenRow();

		wipeAssignedWhen();
		runRepair(assignedWhenRepairJob);
		runRepair(exceptedAssignedWhenRepairJob);

		assertThat(assignedWhenByCurrentAssignmentId()).isEqualTo(assignments);
		assertThat(openHistoricAssignedWhenById()).isEqualTo(openHistoric);
		assertThat(assignedWhenByCurrentExceptionId()).isEqualTo(exceptions);
		assertThat(openHistoricExceptedAssignedWhenById()).isEqualTo(openHistoricExceptions);
		assertThat(historicAssignmentDao.findById(closedId).orElseThrow().getAssignedWhen())
			.as("a closed version keeps falling back to validFrom").isNull();
	}

	@Test
	@DisplayName("Should report no repair when the row already has a date or its source is gone")
	void shouldReportNoRepairWhenThereIsNothingToDo() {
		testDataFactory.createBasicTestData();
		flushAndClear();

		CurrentAssignment stamped = anyStampedAssignment();
		assertThat(assignedWhenRepairService.repairAssignment(stamped.getId()))
			.as("a row that already has a date is left alone").isFalse();

		// an id no source table can answer for - the state a deleted source assignment leaves behind
		CurrentAssignment orphan = anyStampedAssignment();
		orphan.setAssignmentId(Long.MAX_VALUE);
		currentAssignmentDao.saveAndFlush(orphan);
		wipeAssignedWhen();

		assertThat(assignedWhenRepairService.repairAssignment(orphan.getId())).isFalse();
		flushAndClear();
		assertThat(currentAssignmentDao.findById(orphan.getId()).orElseThrow().getAssignedWhen()).isNull();
	}

	/**
	 * The source re-stamps its timestamp on every edit, so a date resolved today can land after a past
	 * interval's own validTo - which is why the repair only touches open rows.
	 */
	private long addClosedVersionSharingHashWithAnOpenRow() {
		HistoricAssignment open = historicAssignmentDao.findAll().stream()
			.filter(ha -> ha.getValidTo() == null)
			.findFirst()
			.orElseThrow();
		HistoricAssignment closed = new HistoricAssignment();
		closed.setRecordHash(open.getRecordHash());
		closed.setValidFrom(LocalDateTime.now().minusDays(30));
		closed.setValidTo(LocalDateTime.now().minusDays(10));
		closed.setUserUuid(open.getUserUuid());
		closed.setUserRoleId(open.getUserRoleId());
		historicAssignmentDao.saveAndFlush(closed);
		return closed.getId();
	}

	private CurrentAssignment anyStampedAssignment() {
		return currentAssignmentDao.findAll().stream()
			.filter(ca -> ca.getAssignedWhen() != null)
			.findFirst()
			.orElseThrow();
	}

	/** Walks the job the way {@code RepairJobRunner} does, one page at a time until it runs dry. */
	private void runRepair(RepairJob job) {
		long cursor = 0;
		List<Long> ids;
		while (!(ids = job.nextChunk(cursor, 100)).isEmpty()) {
			ids.forEach(job::repair);
			cursor = ids.getLast();
			flushAndClear();
		}
	}

	/** Simulates rows written before V1_375 added the column. */
	private void wipeAssignedWhen() {
		entityManager.createQuery("UPDATE CurrentAssignment c SET c.assignedWhen = null").executeUpdate();
		entityManager.createQuery("UPDATE HistoricAssignment h SET h.assignedWhen = null").executeUpdate();
		entityManager.createQuery("UPDATE CurrentExceptedAssignment c SET c.assignedWhen = null").executeUpdate();
		entityManager.createQuery("UPDATE HistoricExceptedAssignment h SET h.assignedWhen = null").executeUpdate();
		flushAndClear();
	}

	/**
	 * The four cases {@code AssignedWhenRepairService} branches on, so test data that stops covering
	 * one of them fails here rather than quietly narrowing the test.
	 */
	private Set<String> sourceKinds() {
		return currentAssignmentDao.findAll().stream()
			.map(ca -> (ca.getOrgUnit() == null ? "DIRECT" : "OU")
				+ (ca.getRoleGroup() == null ? "_ROLE" : "_ROLE_GROUP"))
			.collect(Collectors.toSet());
	}

	private Map<Long, LocalDateTime> assignedWhenByCurrentAssignmentId() {
		return currentAssignmentDao.findAll().stream()
			.filter(ca -> ca.getAssignedWhen() != null)
			.collect(Collectors.toMap(CurrentAssignment::getId, CurrentAssignment::getAssignedWhen));
	}

	private Map<Long, LocalDateTime> openHistoricAssignedWhenById() {
		return historicAssignmentDao.findAll().stream()
			.filter(ha -> ha.getValidTo() == null && ha.getAssignedWhen() != null)
			.collect(Collectors.toMap(HistoricAssignment::getId, HistoricAssignment::getAssignedWhen));
	}

	private Map<Long, LocalDateTime> assignedWhenByCurrentExceptionId() {
		return currentExceptedAssignmentDao.findAll().stream()
			.filter(cea -> cea.getAssignedWhen() != null)
			.collect(Collectors.toMap(CurrentExceptedAssignment::getId, CurrentExceptedAssignment::getAssignedWhen));
	}

	private Map<Long, LocalDateTime> openHistoricExceptedAssignedWhenById() {
		return historicExceptedAssignmentDao.findAll().stream()
			.filter(hea -> hea.getValidTo() == null && hea.getAssignedWhen() != null)
			.collect(Collectors.toMap(HistoricExceptedAssignment::getId, HistoricExceptedAssignment::getAssignedWhen));
	}
}
