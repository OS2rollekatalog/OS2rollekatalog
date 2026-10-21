package dk.digitalidentity.rc.task.repair;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.log.WarnIfSlowerThan;
import dk.digitalidentity.rc.service.SettingsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs every {@link RepairJob} once per version, one chunk at a time.
 * <p>
 * A job is done when a full pass completes without a failing row; the version it ran at is then
 * stored in settings, and the job stays idle until someone raises {@link RepairJob#version()}. That
 * is what makes a hash formula change cheap: raise the version, and every installation repairs itself
 * in the background instead of through a migration that has to freeze a copy of the hash code.
 * <p>
 * One chunk per tick, not a full pass, keeps the writesets small - the same rhythm the seed tasks use,
 * and the reason Galera tolerates them. Eligible jobs take turns, so a job walking a large table does
 * not hold up a small one behind it.
 * <p>
 * The cursor is stored after every chunk, so a restart resumes the pass instead of walking the table
 * again. A pass on a large table takes CHUNK_SIZE rows per tick and can easily outlast the uptime
 * between two restarts; without a stored cursor it would start over every time and never reach the
 * end. The cursor stops advancing once a row has failed, so the rows from the first failure onwards
 * are walked again on the next startup.
 */
@Slf4j
@Component
@EnableScheduling
public class RepairJobRunner {

	/**
	 * A pass has to visit every candidate row, so the chunk size is what decides how long a repair
	 * takes to reach the end: at 200 rows a table of a million open rows outlasts any sane patience.
	 * 2000 rows of single-row transactions per tick is still a small load next to normal traffic.
	 */
	private static final int CHUNK_SIZE = 2000;

	/** setting_key is VARCHAR(64) and the longest key prefix, REPAIR_JOB_VERSION_, takes 19 of them. */
	private static final int MAX_ID_LENGTH = 45;

	private final List<RepairJob> jobs;
	private final RoleCatalogueConfiguration configuration;
	private final SettingsService settingsService;

	private final Map<String, Progress> progress = new HashMap<>();
	private int nextJobIndex = 0;

	public RepairJobRunner(ObjectProvider<RepairJob> jobs, RoleCatalogueConfiguration configuration,
						   SettingsService settingsService) {
		// ObjectProvider, not List: deleting the last job once its repair has run everywhere is the
		// natural end state, and a required List dependency with no candidates fails the context
		this.jobs = jobs.orderedStream().toList();
		this.configuration = configuration;
		this.settingsService = settingsService;
	}

	@PostConstruct
	void validateJobs() {
		Set<String> ids = new HashSet<>();
		for (RepairJob job : jobs) {
			if (!ids.add(job.id())) {
				throw new IllegalStateException("Two repair jobs share the id " + job.id()
					+ " - they would share one settings key, so one of them would be marked done without running");
			}
			if (job.id().length() > MAX_ID_LENGTH) {
				throw new IllegalStateException("Repair job id " + job.id() + " is longer than " + MAX_ID_LENGTH
					+ " characters and would not fit the settings key");
			}
			if (job.version() < 1) {
				throw new IllegalStateException("Repair job " + job.id() + " must have a version of at least 1"
					+ " - version 0 is what an unrun job reads back as, so it would never run");
			}
		}
	}

	@Scheduled(fixedDelay = 30_000)
	@WarnIfSlowerThan(millis = 10 * 1000)
	public void runChunk() {
		if (!configuration.getScheduled().isEnabled() || jobs.isEmpty()) {
			return;
		}

		// take turns from where the last tick left off, so one long pass cannot starve the others
		for (int attempt = 0; attempt < jobs.size(); attempt++) {
			RepairJob job = jobs.get(nextJobIndex);
			nextJobIndex = (nextJobIndex + 1) % jobs.size();

			Progress state = progress.computeIfAbsent(job.id(), _ -> new Progress());
			if (state.done || settingsService.getRepairJobVersion(job.id()) >= job.version()) {
				state.done = true;
				continue;
			}

			if (!state.started) {
				state.started = true;
				state.cursor = settingsService.getRepairJobCursor(job.id(), job.version());
				log.info("Repair job {} (v{}) starting after id={}: {}",
					job.id(), job.version(), state.cursor, job.description());
			}

			runChunk(job, state);
			return;
		}
	}

	private void runChunk(RepairJob job, Progress state) {
		List<Long> ids = job.nextChunk(state.cursor, CHUNK_SIZE);

		if (ids.isEmpty()) {
			finish(job, state);
			return;
		}

		for (Long id : ids) {
			try {
				if (job.repair(id)) {
					state.repaired++;
				}
			}
			catch (Exception ex) {
				// skip the row rather than stop the pass - retrying it now would just loop every tick
				state.failed++;
				log.error("Repair job {} failed on id={}", job.id(), id, ex);
			}
		}

		state.cursor = ids.get(ids.size() - 1);
		state.visited += ids.size();
		if (state.failed == 0) {
			// stored only while the pass is clean: a restart has to walk the failed row again, and the
			// chunk it sits in is the cheapest resume point we can name without tracking it separately
			settingsService.setRepairJobCursor(job.id(), job.version(), state.cursor);
		}
		log.info("Repair job {} at id={}: {} rows visited, {} repaired, {} failed",
			job.id(), state.cursor, state.visited, state.repaired, state.failed);
	}

	private void finish(RepairJob job, Progress state) {
		if (state.failed > 0) {
			// leave the version unset so the pass is retried on the next application start, and stop
			// ticking until then: the rows we could not repair will not start working within this process
			state.done = true;
			log.warn("Repair job {} (v{}) ended with {} failed rows, {} repaired - retrying on next startup: {}",
				job.id(), job.version(), state.failed, state.repaired, job.description());
			return;
		}

		// done only once the version is safely stored - a failing write here (certification conflict,
		// deadlock on setting) has to be retried on the next tick, not swallowed for the whole process
		settingsService.setRepairJobVersion(job.id(), job.version());
		state.done = true;
		log.info("Repair job {} (v{}) finished: {} rows visited, {} repaired - {}",
			job.id(), job.version(), state.visited, state.repaired, job.description());
	}

	private static final class Progress {
		private long cursor = 0;
		private long visited = 0;
		private long repaired = 0;
		private long failed = 0;
		private boolean started = false;
		private boolean done = false;
	}
}
