package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.task.assignment.RepairHistoricItSystemAssignmentCollectionsTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Engangs-reconcile af {@code attestation_attestation_responsible_collection} mod den levende
 * {@code it_system_attestation_responsible}-tabel.
 * <p>
 * V1_352 seedede collections ud fra HISTORISKE attesteringsdata (responsible_user_uuid på de
 * temporale snapshot-tabeller), ikke fra den levende ansvarsliste. Systemer der engang havde en
 * systemansvarlig men ikke længere har det, endte derfor med en ikke-tom collection og optræder
 * stadig i attesterings-admin-overblikket, selvom ingen aktuelt kan attestere dem (bruger-visningen
 * skjuler dem via et live-krydstjek, og påmindelses-mails resolves fra den forældede collection).
 * <p>
 * Tasken gennemgår alle collections med et it-system og bringer hver divergerende collection i sync
 * via {@link ItSystemService#reconcileAttestationResponsibleCollection(long)}, som genbruger den
 * veletablerede {@code updateAttestationResponsibles}-sti (sætter collection = levende liste og
 * sletter åbne attesteringer når listen er tom — inkl. korrekt oprydning af attesterings-børnerækker,
 * der ikke har ON DELETE CASCADE).
 * <p>
 * Bevidst som chunked baggrunds-task frem for Flyway-migration: oprydningen kræver Spring-service-laget
 * (Flyway kører på rå JDBC), jf. mønstret fra {@link RepairHistoricItSystemAssignmentCollectionsTask}.
 * Et fuldført gennemløb uden fejl markeres persistent i settings — ellers ville hver app-start lave et
 * fuldt no-op-gennemløb. Fejler enkelte systemer, sættes markøren ikke, og de får et nyt forsøg ved
 * næste app-start.
 * <p>
 * <b>Kan fjernes</b> når alle miljøer har kørt oprydningen fejlfrit én gang (dvs. settings-nøglen
 * {@code AttestationResponsibleCollectionReconcilePerformed} er sat overalt). Løbende oprydning er ikke
 * nødvendig, fordi ingen kode længere skaber spøgelses-collections: den eneste kilde var den historiske
 * seeding i migrering V1_352, og alle øvrige ændringer af ansvarlige går gennem
 * {@link ItSystemService#updateAttestationResponsibles} (som holder collectionen i sync). Når tasken
 * fjernes, kan følgende støtte-kode fjernes sammen med den, hvis den ikke bruges andetsteds:
 * {@link ItSystemService#reconcileAttestationResponsibleCollection(long)},
 * {@link AttestationResponsibleCollectionDao#findAllItSystemIds()} og settings-nøglen
 * {@code SETTING_ATTESTATION_RESPONSIBLE_COLLECTION_RECONCILE_PERFORMED} med tilhørende
 * getter/setter i {@code SettingsService}. Den kan tidligst fjernes i en release efter den, hvor
 * oprydningen udkom, så alle installationer garanteret har kørt den.
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class ReconcileAttestationResponsibleCollectionsTask {

	private final RoleCatalogueConfiguration configuration;
	private final AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;
	private final ItSystemService itSystemService;
	private final SettingsService settingsService;

	private volatile boolean done = false;

	@Scheduled(fixedDelay = 60_000)
	public void reconcileChunk() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		if (done) {
			return;
		}
		if (settingsService.isAttestationResponsibleCollectionReconcilePerformed()) {
			done = true;
			return;
		}

		log.info("Starting one-shot reconcile of attestation responsible collections against live responsibles");
		List<Long> itSystemIds = attestationResponsibleCollectionDao.findAllItSystemIds();
		int reconciled = 0;
		int failed = 0;
		// Per-system transaktion via @Transactional på service-metoden: ét dårligt system må ikke
		// rulle hele gennemløbet tilbage.
		for (Long itSystemId : itSystemIds) {
			try {
				if (itSystemService.reconcileAttestationResponsibleCollection(itSystemId)) {
					reconciled++;
				}
			} catch (Exception e) {
				failed++;
				log.warn("Failed to reconcile responsible collection for IT system id={} — will retry on next app start", itSystemId, e);
			}
		}

		log.info("Reconcile of attestation responsible collections complete: {} reconciled ({} failed)", reconciled, failed);
		if (failed == 0) {
			// Markér kun fuldført ved fejlfrit gennemløb — fejlede systemer skal have et nyt forsøg
			// ved næste app-start, hvor markøren stadig er fraværende.
			settingsService.setAttestationResponsibleCollectionReconcilePerformed();
		}
		done = true;
	}
}
