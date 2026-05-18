package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.serializer.SystemRoleAssignmentDao;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Engangs-seed af {@code historic_it_system_assignment} for eksisterende {@link SystemRoleAssignment}-rækker.
 * <p>
 * Tabellen blev introduceret i V1_336 og populeres udelukkende event-drevet via de to hooks i
 * {@code UpdatedAssignmentCalculatorHook}. Der er ingen Flyway-seed; tabellen er derfor tom efter
 * deploy indtil en admin tilfældigvis tilføjer eller fjerner en systemrolle på en UserRole, hvilket
 * får {@code SystemRoleAssignmentsUpdaterJdbc} til at smide "No system assignments for date X in the
 * history tables." på hver kørsel af attestation-tasken.
 * <p>
 * Bevidst som baggrunds-task frem for Flyway-migration: Galera er følsomt over for store writesets,
 * jf. mønstret fra {@link CleanUpDeletedUserCurrentAssignmentsTask}. Hver tick processerer en chunk
 * af SRA'er; hver SRA seedes i sin egen tx via service-metoden, så én dårlig række ikke ruller hele
 * chunk'en tilbage. Engangs-natur: tasken stopper når der ikke er flere SRA'er uden åben
 * historic-række og forbliver stille indtil næste app-start.
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class SeedHistoricItSystemAssignmentsTask {

	private static final int CHUNK_SIZE = 200;

	private final RoleCatalogueConfiguration configuration;
	private final SystemRoleAssignmentDao systemRoleAssignmentDao;
	private final HistoricItSystemAssignmentService historicItSystemAssignmentService;

	private volatile boolean done = false;
	private boolean started = false;
	private long totalProcessed = 0;
	// SRA-IDs der konsistent fejler. Holdes i hukommelsen så tasken ikke spinner evigt på de samme
	// rækker — query'en returnerer dem hver tick (de har stadig ingen åben historic-række), men vi
	// springer dem over efter første fejl. Genstart resetter — så transiente fejl får et nyt forsøg.
	private final Set<Long> permanentlyFailed = new HashSet<>();

	@Scheduled(fixedDelay = 30_000)
	public void seedChunk() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		if (done) {
			return;
		}

		List<Long> ids = systemRoleAssignmentDao.findIdsMissingOpenHistoricRow(PageRequest.of(0, CHUNK_SIZE));
		List<Long> processable = ids.stream().filter(id -> !permanentlyFailed.contains(id)).toList();
		if (processable.isEmpty()) {
			if (started) {
				log.info("Seed of historic_it_system_assignment complete: {} rows seeded in total ({} permanently failed)",
					totalProcessed, permanentlyFailed.size());
			}
			done = true;
			return;
		}

		if (!started) {
			log.info("Starting one-shot seed of historic_it_system_assignment for existing system role assignments");
			started = true;
		}

		int seededThisChunk = 0;
		int failedThisChunk = 0;
		// Bevidst per-item transaktion via @Transactional på service-metoden frem for én chunk-tx:
		// hvis én SRA fejler (lazy-load NPE, FK-overtrædelse osv.) skal vi ikke rulle hele chunk'en
		// tilbage og hænge fast i den samme 200-er batch ved næste tick. Service-metoden loader
		// SRA inden for tx så lazy-properties (UserRole.itSystem) kan tilgås uden session-fejl.
		for (Long id : processable) {
			try {
				if (historicItSystemAssignmentService.seedHistoricRowFromSystemRoleAssignmentId(id)) {
					seededThisChunk++;
				}
			} catch (Exception e) {
				failedThisChunk++;
				permanentlyFailed.add(id);
				log.warn("Failed to seed historic_it_system_assignment for SystemRoleAssignment id={} — skipping on subsequent ticks", id, e);
			}
		}
		totalProcessed += seededThisChunk;
		if (failedThisChunk > 0) {
			log.info("Seeded chunk of {} historic_it_system_assignment rows ({} failed) (running total: {})", seededThisChunk, failedThisChunk, totalProcessed);
		} else {
			log.info("Seeded chunk of {} historic_it_system_assignment rows (running total: {})", seededThisChunk, totalProcessed);
		}
	}
}
