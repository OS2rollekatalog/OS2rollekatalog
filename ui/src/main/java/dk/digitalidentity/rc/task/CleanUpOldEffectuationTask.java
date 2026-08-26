package dk.digitalidentity.rc.task;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ManualAssignmentEffectuationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deletes completed (COMPLETED) manual-assignment-effectuation tasks older than
 * rc.effectuationConfig.cleanUpRetentionMonths (default 3 months). Pending (PENDING) tasks are
 * never deleted regardless of age. The completion event itself remains discoverable afterwards
 * via the audit log, so this is a pure storage-cleanup task.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class CleanUpOldEffectuationTask {
	private final ManualAssignmentEffectuationService manualAssignmentEffectuationService;
	private final RoleCatalogueConfiguration configuration;

	@Scheduled(cron = "${rc.effectuationConfig.cleanUpTaskCron:#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 1 * * ?}")
	public void cleanUpOldEffectuations() {
		if (!configuration.getScheduled().isEnabled()) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}
		
		log.info("Running scheduled job");

		int retentionMonths = configuration.getEffectuationConfig().getCleanUpRetentionMonths();
		LocalDateTime cutoff = LocalDateTime.now().minusMonths(retentionMonths);
		long deleted = manualAssignmentEffectuationService.deleteEffectuatedOlderThan(cutoff);

		if (deleted > 0) {
			log.info("Deleted {} completed manual-assignment-effectuation tasks older than {} months", deleted, retentionMonths);
		}
	}
}
