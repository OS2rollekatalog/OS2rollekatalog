package dk.digitalidentity.rc.task.repair;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.Scheduled;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairJobRunnerTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private Scheduled scheduled;

	@Mock
	private SettingsService settingsService;

	private RecordingJob job;

	@BeforeEach
	void setUp() {
		job = new RecordingJob();
		lenient().when(configuration.getScheduled()).thenReturn(scheduled);
		lenient().when(scheduled.isEnabled()).thenReturn(true);
	}

	@SuppressWarnings("unchecked")
	private RepairJobRunner runner(RepairJob... registered) {
		List<RepairJob> jobs = List.of(registered.length == 0 ? new RepairJob[]{job} : registered);
		ObjectProvider<RepairJob> provider = mock(ObjectProvider.class);
		when(provider.orderedStream()).thenReturn(jobs.stream());
		RepairJobRunner runner = new RepairJobRunner(provider, configuration, settingsService);
		runner.validateJobs();
		return runner;
	}

	@Test
	@DisplayName("a job that already completed at its current version is not run again")
	void completedJobIsSkipped() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(1);

		runner().runChunk();

		assertThat(job.chunkRequests).isEmpty();
		verify(settingsService, never()).setRepairJobVersion(anyString(), anyInt());
	}

	@Test
	@DisplayName("raising the version runs the job again from the start")
	void raisedVersionRunsAgain() {
		job.version = 2;
		when(settingsService.getRepairJobVersion("recording")).thenReturn(1);
		job.chunks = List.of(List.of(7L, 9L), List.of());

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();

		assertThat(job.chunkRequests).containsExactly(0L, 9L);
		assertThat(job.repairedIds).containsExactly(7L, 9L);
		verify(settingsService).setRepairJobVersion("recording", 2);
	}

	@Test
	@DisplayName("the cursor advances past the last id of the chunk, and the empty chunk completes the pass")
	void cursorAdvancesAndPassCompletes() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		job.chunks = List.of(List.of(4L, 11L), List.of(15L), List.of());

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();
		runner.runChunk();

		assertThat(job.chunkRequests).containsExactly(0L, 11L, 15L);
		assertThat(job.repairedIds).containsExactly(4L, 11L, 15L);
		verify(settingsService).setRepairJobVersion("recording", 1);
	}

	@Test
	@DisplayName("a restart resumes the pass from the stored cursor instead of walking the table again")
	void passResumesFromStoredCursor() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		when(settingsService.getRepairJobCursor("recording", 1)).thenReturn(4200L);
		job.chunks = List.of(List.of(4300L), List.of());

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();

		assertThat(job.chunkRequests).containsExactly(4200L, 4300L);
		verify(settingsService).setRepairJobVersion("recording", 1);
	}

	@Test
	@DisplayName("the cursor is stored after every chunk")
	void cursorIsStoredAfterEveryChunk() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		job.chunks = List.of(List.of(4L, 11L), List.of(15L), List.of());

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();
		runner.runChunk();

		verify(settingsService).setRepairJobCursor("recording", 1, 11L);
		verify(settingsService).setRepairJobCursor("recording", 1, 15L);
	}

	@Test
	@DisplayName("the stored cursor stops advancing once a row has failed, so the failing row is walked again")
	void failingRowFreezesTheStoredCursor() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		job.chunks = List.of(List.of(3L), List.of(5L, 8L), List.of());
		job.failOn = 5L;

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();
		runner.runChunk();

		// only the clean chunk is stored - a restart picks up at 3 and retries 5
		verify(settingsService).setRepairJobCursor("recording", 1, 3L);
		verify(settingsService, times(1)).setRepairJobCursor(anyString(), anyInt(), anyLong());
	}

	@Test
	@DisplayName("a failing row is skipped and leaves the version unset, and the job stops until restart")
	void failingRowDoesNotCompleteThePass() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		job.chunks = List.of(List.of(3L, 5L), List.of());
		job.failOn = 3L;

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();
		runner.runChunk();

		// the rest of the chunk is still repaired, and the failing row is not retried in a tight loop
		assertThat(job.repairedIds).containsExactly(5L);
		assertThat(job.chunkRequests).containsExactly(0L, 5L);
		verify(settingsService, never()).setRepairJobVersion(anyString(), anyInt());
	}

	@Test
	@DisplayName("a failing version write is retried on the next tick instead of ending the job")
	void failingVersionWriteIsRetried() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		job.chunks = List.of(List.of());
		doThrow(new IllegalStateException("boom")).doNothing()
			.when(settingsService).setRepairJobVersion("recording", 1);

		RepairJobRunner runner = runner();
		assertThatThrownBy(runner::runChunk).isInstanceOf(IllegalStateException.class);
		runner.runChunk();

		verify(settingsService, times(2)).setRepairJobVersion("recording", 1);
	}

	@Test
	@DisplayName("nothing runs while scheduling is disabled")
	void schedulingDisabledStopsEverything() {
		when(scheduled.isEnabled()).thenReturn(false);

		runner().runChunk();

		assertThat(job.chunkRequests).isEmpty();
		verify(settingsService, never()).getRepairJobVersion(anyString());
	}

	@Test
	@DisplayName("eligible jobs take turns instead of the first one holding the ticks")
	void eligibleJobsTakeTurns() {
		RecordingJob other = new RecordingJob("other");
		other.chunks = List.of(List.of(2L), List.of());
		job.chunks = List.of(List.of(1L), List.of());
		when(settingsService.getRepairJobVersion(anyString())).thenReturn(0);

		RepairJobRunner runner = runner(job, other);
		runner.runChunk();
		runner.runChunk();

		assertThat(job.repairedIds).containsExactly(1L);
		assertThat(other.repairedIds).containsExactly(2L);
	}

	@Test
	@DisplayName("a completed job is not looked up in settings on every later tick")
	void completedJobStopsQueryingSettings() {
		when(settingsService.getRepairJobVersion("recording")).thenReturn(0);
		job.chunks = List.of(List.of());

		RepairJobRunner runner = runner();
		runner.runChunk();
		runner.runChunk();
		runner.runChunk();

		verify(settingsService, times(1)).getRepairJobVersion("recording");
	}

	@Test
	@DisplayName("two jobs with the same id are refused at startup")
	void duplicateIdsAreRefused() {
		assertThatThrownBy(() -> runner(job, new RecordingJob("recording")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("share the id");
	}

	@Test
	@DisplayName("a job at version 0 is refused at startup - it would read back as already done")
	void versionZeroIsRefused() {
		job.version = 0;

		assertThatThrownBy(this::runner)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("version of at least 1");
	}

	private static final class RecordingJob implements RepairJob {
		private final String id;
		private final List<Long> chunkRequests = new ArrayList<>();
		private final List<Integer> chunkLimits = new ArrayList<>();
		private final List<Long> repairedIds = new ArrayList<>();
		private List<List<Long>> chunks = List.of(List.of());
		private int version = 1;
		private Long failOn;
		private int chunkIndex = 0;

		private RecordingJob() {
			this("recording");
		}

		private RecordingJob(String id) {
			this.id = id;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public int version() {
			return version;
		}

		@Override
		public String description() {
			return "test job";
		}

		@Override
		public List<Long> nextChunk(long afterId, int limit) {
			chunkRequests.add(afterId);
			chunkLimits.add(limit);
			return chunks.get(Math.min(chunkIndex++, chunks.size() - 1));
		}

		@Override
		public boolean repair(long id) {
			if (failOn != null && failOn == id) {
				throw new IllegalStateException("boom");
			}
			repairedIds.add(id);
			return true;
		}
	}
}
