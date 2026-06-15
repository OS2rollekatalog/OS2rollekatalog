package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Engangs-reparation af {@code historic_it_system_assignment.responsible_collection_id} (+ recordHash).
 * <p>
 * Rolleopbygnings-attestering (IT_SYSTEM_ROLES_ATTESTATION) oprettes kun for rækker med et
 * collection-id, men id'et blev fejlagtigt kun stemplet når rollen havde tildelings-krydset
 * ({@code roleAssignmentAttestationByAttestationResponsible}) — et kryds der alene styrer hvem der
 * attesterer <i>tildelinger</i>. Systemansvarlige fik derfor ingen rolleopbygnings-attesteringer
 * for roller uden krydset. Derudover satte den tidligere SQL-backfill collection-id uden at
 * genberegne recordHash (som indeholder collection-id), så fjern-events ikke kunne lukke rækkerne.
 * <p>
 * Tasken gennemgår alle åbne rækker for it-systemer med en responsible-collection og stempler
 * collection-id + konsistent hash via {@link HistoricItSystemAssignmentService#repairResponsibleCollectionRow}.
 * Bevidst som chunked baggrunds-task frem for Flyway-migration: hashen kan kun beregnes i Java, og
 * Galera er følsomt over for store writesets, jf. mønstret fra {@link SeedHistoricItSystemAssignmentsTask}.
 * Keyset-cursor ({@code id > lastProcessedId}) sikrer at hver række kun besøges én gang pr. gennemløb.
 * Kandidat-query'en kan ikke skelne reparerede rækker fra ureparerede (hash-tjekket kræver Java), så
 * et fuldført gennemløb uden fejl markeres persistent i settings — ellers ville hver app-start lave
 * et fuldt no-op-gennemløb af alle åbne rækker. Fejler enkelte rækker, sættes markøren ikke, og de
 * får et nyt forsøg ved næste app-start.
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class RepairHistoricItSystemAssignmentCollectionsTask {

	private static final int CHUNK_SIZE = 200;

	private final RoleCatalogueConfiguration configuration;
	private final HistoricItSystemAssignmentDao historicItSystemAssignmentDao;
	private final HistoricItSystemAssignmentService historicItSystemAssignmentService;
	private final SettingsService settingsService;

	private volatile boolean done = false;
	private boolean started = false;
	private long lastProcessedId = 0;
	private long totalRepaired = 0;
	private long totalFailed = 0;

	@Scheduled(fixedDelay = 30_000)
	public void repairChunk() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		if (done) {
			return;
		}
		if (settingsService.isHistoricItSystemAssignmentCollectionRepairPerformed()) {
			done = true;
			return;
		}

		List<Long> ids = historicItSystemAssignmentDao.findOpenIdsForItSystemsWithResponsibleCollection(
			lastProcessedId, PageRequest.of(0, CHUNK_SIZE));
		if (ids.isEmpty()) {
			if (started) {
				log.info("Repair of historic_it_system_assignment responsible collections complete: {} rows repaired ({} failed)",
					totalRepaired, totalFailed);
			}
			if (totalFailed == 0) {
				// Markér kun fuldført ved fejlfrit gennemløb — fejlede rækker skal have et nyt
				// forsøg ved næste app-start, hvor markøren stadig er fraværende.
				settingsService.setHistoricItSystemAssignmentCollectionRepairPerformed();
			}
			done = true;
			return;
		}

		if (!started) {
			log.info("Starting one-shot repair of historic_it_system_assignment responsible collection ids");
			started = true;
		}

		int repairedThisChunk = 0;
		// Per-række transaktion via @Transactional på service-metoden: én dårlig række må ikke
		// rulle hele chunk'en tilbage. Cursoren rykkes uanset udfald, så en fejlende række ikke
		// blokerer gennemløbet — den får et nyt forsøg ved næste app-start.
		for (Long id : ids) {
			try {
				if (historicItSystemAssignmentService.repairResponsibleCollectionRow(id)) {
					repairedThisChunk++;
				}
			} catch (Exception e) {
				totalFailed++;
				log.warn("Failed to repair historic_it_system_assignment id={} — will retry on next app start", id, e);
			}
			lastProcessedId = id;
		}
		totalRepaired += repairedThisChunk;
		if (repairedThisChunk > 0) {
			log.info("Repaired chunk of {} historic_it_system_assignment rows (running total: {})", repairedThisChunk, totalRepaired);
		}
	}
}
