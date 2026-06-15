package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.Scheduled;
import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.service.SettingsService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairHistoricItSystemAssignmentCollectionsTaskTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private Scheduled scheduled;

	@Mock
	private HistoricItSystemAssignmentDao historicItSystemAssignmentDao;

	@Mock
	private HistoricItSystemAssignmentService historicItSystemAssignmentService;

	@Mock
	private SettingsService settingsService;

	@InjectMocks
	private RepairHistoricItSystemAssignmentCollectionsTask task;

	@BeforeEach
	void setup() {
		when(configuration.getScheduled()).thenReturn(scheduled);
		when(scheduled.isEnabled()).thenReturn(true);
		lenient().when(settingsService.isHistoricItSystemAssignmentCollectionRepairPerformed()).thenReturn(false);
	}

	@Test
	@DisplayName("delegates each row id in the chunk to the repair service")
	void delegatesChunkToService() {
		when(historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(eq(0L), any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		when(historicItSystemAssignmentService.repairResponsibleCollectionRow(anyLong())).thenReturn(true);

		task.repairChunk();

		verify(historicItSystemAssignmentService).repairResponsibleCollectionRow(1L);
		verify(historicItSystemAssignmentService).repairResponsibleCollectionRow(2L);
	}

	@Test
	@DisplayName("advances the keyset cursor between chunks")
	void advancesCursorBetweenChunks() {
		when(historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(eq(0L), any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		when(historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(eq(2L), any(Pageable.class)))
			.thenReturn(List.of());
		when(historicItSystemAssignmentService.repairResponsibleCollectionRow(anyLong())).thenReturn(false);

		task.repairChunk();
		task.repairChunk();

		verify(historicItSystemAssignmentDao).findOpenIdsForItSystemsWithResponsibleCollection(eq(2L), any(Pageable.class));
	}

	@Test
	@DisplayName("marks itself done, records the settings marker and stops querying when the dao returns an empty chunk")
	void stopsWhenChunkEmpty() {
		when(historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(anyLong(), any(Pageable.class)))
			.thenReturn(List.of());

		task.repairChunk();
		task.repairChunk();

		verify(historicItSystemAssignmentDao, times(1)).findOpenIdsForItSystemsWithResponsibleCollection(anyLong(), any(Pageable.class));
		verify(historicItSystemAssignmentService, never()).repairResponsibleCollectionRow(anyLong());
		verify(settingsService).setHistoricItSystemAssignmentCollectionRepairPerformed();
	}

	@Test
	@DisplayName("skips entirely when the settings marker from a previous complete run is set")
	void skipsWhenMarkerAlreadySet() {
		when(settingsService.isHistoricItSystemAssignmentCollectionRepairPerformed()).thenReturn(true);

		task.repairChunk();
		task.repairChunk();

		verify(historicItSystemAssignmentDao, never()).findOpenIdsForItSystemsWithResponsibleCollection(anyLong(), any(Pageable.class));
		// Markøren tjekkes kun én gang — anden tick stopper på det interne done-flag.
		verify(settingsService, times(1)).isHistoricItSystemAssignmentCollectionRepairPerformed();
	}

	@Test
	@DisplayName("does nothing when scheduled tasks are disabled")
	void noopWhenScheduledDisabled() {
		when(scheduled.isEnabled()).thenReturn(false);

		task.repairChunk();

		verify(historicItSystemAssignmentDao, never()).findOpenIdsForItSystemsWithResponsibleCollection(anyLong(), any(Pageable.class));
	}

	@Test
	@DisplayName("a failing row does not stop the chunk, the cursor advances past it, and the completion marker is NOT set")
	void failingRowDoesNotPoisonChunk() {
		when(historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(eq(0L), any(Pageable.class)))
			.thenReturn(List.of(1L, 2L));
		doThrow(new RuntimeException("simulated DB failure"))
			.when(historicItSystemAssignmentService).repairResponsibleCollectionRow(eq(1L));
		when(historicItSystemAssignmentService.repairResponsibleCollectionRow(eq(2L))).thenReturn(true);
		when(historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(eq(2L), any(Pageable.class)))
			.thenReturn(List.of());

		task.repairChunk();
		task.repairChunk();

		// Række 2 behandles selvom række 1 fejlede, og næste chunk starter efter id 2 — den
		// fejlende række ligger bag cursoren. Markøren sættes ikke, så rækken får et nyt
		// forsøg ved næste app-start.
		verify(historicItSystemAssignmentService).repairResponsibleCollectionRow(2L);
		verify(historicItSystemAssignmentDao).findOpenIdsForItSystemsWithResponsibleCollection(eq(2L), any(Pageable.class));
		verify(settingsService, never()).setHistoricItSystemAssignmentCollectionRepairPerformed();
	}
}
