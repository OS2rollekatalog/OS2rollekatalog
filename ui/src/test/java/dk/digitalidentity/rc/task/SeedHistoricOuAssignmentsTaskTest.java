package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.Scheduled;
import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedHistoricOuAssignmentsTaskTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private Scheduled scheduled;

	@Mock
	private OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;

	@Mock
	private OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;

	@Mock
	private HistoricOuAssignmentService historicOuAssignmentService;

	@InjectMocks
	private SeedHistoricOuAssignmentsTask task;

	@BeforeEach
	void setup() {
		when(configuration.getScheduled()).thenReturn(scheduled);
		when(scheduled.isEnabled()).thenReturn(true);
	}

	@Test
	@DisplayName("delegates each user-role and role-group id in the chunk to the service")
	void delegatesBothQueuesToService() {
		when(orgUnitUserRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		when(orgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(11L));
		when(historicOuAssignmentService.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(anyLong())).thenReturn(true);
		when(historicOuAssignmentService.seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(anyLong())).thenReturn(true);

		task.seedChunk();

		verify(historicOuAssignmentService).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(1L);
		verify(historicOuAssignmentService).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(2L);
		verify(historicOuAssignmentService).seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(11L);
	}

	@Test
	@DisplayName("marks itself done and stops querying when both queues return an empty chunk")
	void stopsWhenBothQueuesEmpty() {
		when(orgUnitUserRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of());
		when(orgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of());

		task.seedChunk();
		task.seedChunk();

		verify(orgUnitUserRoleAssignmentDao, times(1)).findIdsMissingOpenHistoricRow(any(Pageable.class));
		verify(orgUnitRoleGroupAssignmentDao, times(1)).findIdsMissingOpenHistoricRow(any(Pageable.class));
		verify(historicOuAssignmentService, never()).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(anyLong());
		verify(historicOuAssignmentService, never()).seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(anyLong());
	}

	@Test
	@DisplayName("keeps polling the role-group queue when the user-role queue is already drained")
	void independentDrainedFlags() {
		// user-role kø: tom fra start. role-group kø: én ID i tick 1, tom i tick 2.
		when(orgUnitUserRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of());
		when(orgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(11L))
			.thenReturn(List.of());
		when(historicOuAssignmentService.seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(11L)).thenReturn(true);

		task.seedChunk();
		task.seedChunk();
		task.seedChunk();

		// user-role queried kun én gang (drained efter tick 1), role-group queried to gange (tick 1 + tick 2).
		verify(orgUnitUserRoleAssignmentDao, times(1)).findIdsMissingOpenHistoricRow(any(Pageable.class));
		verify(orgUnitRoleGroupAssignmentDao, times(2)).findIdsMissingOpenHistoricRow(any(Pageable.class));
		verify(historicOuAssignmentService, times(1)).seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(11L);
	}

	@Test
	@DisplayName("does nothing when scheduled tasks are disabled")
	void noopWhenScheduledDisabled() {
		when(scheduled.isEnabled()).thenReturn(false);

		task.seedChunk();

		verify(orgUnitUserRoleAssignmentDao, never()).findIdsMissingOpenHistoricRow(any(Pageable.class));
		verify(orgUnitRoleGroupAssignmentDao, never()).findIdsMissingOpenHistoricRow(any(Pageable.class));
	}

	@Test
	@DisplayName("a failing assignment in one queue does not stop processing of the rest of the chunk")
	void failingAssignmentDoesNotPoisonChunk() {
		when(orgUnitUserRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		when(orgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of());
		doThrow(new RuntimeException("simulated DB failure"))
			.when(historicOuAssignmentService).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(eq(1L));
		when(historicOuAssignmentService.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(eq(2L))).thenReturn(true);

		task.seedChunk();

		// The second assignment must still be processed even though the first one threw.
		verify(historicOuAssignmentService).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(2L);
	}

	@Test
	@DisplayName("permanently failing ids are skipped on subsequent ticks")
	void permanentlyFailedIdsSkippedNextTick() {
		when(orgUnitUserRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(1L));
		when(orgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of());
		doThrow(new RuntimeException("always fails"))
			.when(historicOuAssignmentService).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(eq(1L));

		task.seedChunk();
		task.seedChunk();

		// Service kun kaldt én gang: tick 2 ser id'et i permanentlyFailed og springer det over,
		// processable bliver tom og user-role-køen markeres som done.
		verify(historicOuAssignmentService, times(1)).seedHistoricRowsFromOrgUnitUserRoleAssignmentId(anyLong());
	}
}
