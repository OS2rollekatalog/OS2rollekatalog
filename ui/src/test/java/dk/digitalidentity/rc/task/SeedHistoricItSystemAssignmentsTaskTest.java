package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.Scheduled;
import dk.digitalidentity.rc.dao.serializer.SystemRoleAssignmentDao;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
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
class SeedHistoricItSystemAssignmentsTaskTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private Scheduled scheduled;

	@Mock
	private SystemRoleAssignmentDao systemRoleAssignmentDao;

	@Mock
	private HistoricItSystemAssignmentService historicItSystemAssignmentService;

	@InjectMocks
	private SeedHistoricItSystemAssignmentsTask task;

	@BeforeEach
	void setup() {
		when(configuration.getScheduled()).thenReturn(scheduled);
		when(scheduled.isEnabled()).thenReturn(true);
	}

	@Test
	@DisplayName("delegates each SRA id in the chunk to the service")
	void delegatesChunkToService() {
		when(systemRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		when(historicItSystemAssignmentService.seedHistoricRowFromSystemRoleAssignmentId(anyLong())).thenReturn(true);

		task.seedChunk();

		verify(historicItSystemAssignmentService).seedHistoricRowFromSystemRoleAssignmentId(1L);
		verify(historicItSystemAssignmentService).seedHistoricRowFromSystemRoleAssignmentId(2L);
	}

	@Test
	@DisplayName("marks itself done and stops querying when the dao returns an empty chunk")
	void stopsWhenChunkEmpty() {
		when(systemRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of());

		task.seedChunk();
		task.seedChunk();

		verify(systemRoleAssignmentDao, times(1)).findIdsMissingOpenHistoricRow(any(Pageable.class));
		verify(historicItSystemAssignmentService, never()).seedHistoricRowFromSystemRoleAssignmentId(anyLong());
	}

	@Test
	@DisplayName("does nothing when scheduled tasks are disabled")
	void noopWhenScheduledDisabled() {
		when(scheduled.isEnabled()).thenReturn(false);

		task.seedChunk();

		verify(systemRoleAssignmentDao, never()).findIdsMissingOpenHistoricRow(any(Pageable.class));
	}

	@Test
	@DisplayName("a failing SRA does not stop processing of the rest of the chunk")
	void failingSraDoesNotPoisonChunk() {
		when(systemRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		doThrow(new RuntimeException("simulated DB failure"))
			.when(historicItSystemAssignmentService).seedHistoricRowFromSystemRoleAssignmentId(eq(1L));
		when(historicItSystemAssignmentService.seedHistoricRowFromSystemRoleAssignmentId(eq(2L))).thenReturn(true);

		task.seedChunk();

		// The second SRA must still be processed even though the first one threw
		verify(historicItSystemAssignmentService).seedHistoricRowFromSystemRoleAssignmentId(2L);
	}

	@Test
	@DisplayName("permanently failing ids are skipped on subsequent ticks")
	void permanentlyFailedIdsSkippedNextTick() {
		when(systemRoleAssignmentDao.findIdsMissingOpenHistoricRow(any(Pageable.class)))
			.thenReturn(List.of(1L));
		doThrow(new RuntimeException("always fails"))
			.when(historicItSystemAssignmentService).seedHistoricRowFromSystemRoleAssignmentId(eq(1L));

		task.seedChunk();
		task.seedChunk();

		// Service should only be called once: tick 2 sees the id in permanentlyFailed and skips it
		verify(historicItSystemAssignmentService, times(1)).seedHistoricRowFromSystemRoleAssignmentId(anyLong());
	}
}
