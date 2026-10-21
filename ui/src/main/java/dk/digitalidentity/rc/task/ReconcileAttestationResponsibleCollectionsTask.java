package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.log.WarnIfSlowerThan;
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
 * One-shot reconcile of {@code attestation_attestation_responsible_collection} against the live
 * {@code it_system_attestation_responsible} table, syncing every divergent collection via
 * {@link ItSystemService#reconcileAttestationResponsibleCollection(long)}.
 * <p>
 * V1_352 seeded the collections from historic attestation rows - the union of everyone ever
 * responsible for a system - rather than from the live list. Since recipients resolve from the
 * collection while page access is gated on the live list, a drifted collection either mails a former
 * responsible who is then denied access, or keeps a system nobody can attest in the admin overview.
 * <p>
 * A background task rather than a Flyway migration because the cleanup needs the Spring service layer
 * (Flyway runs on raw JDBC), following {@link RepairHistoricItSystemAssignmentCollectionsTask}. A pass
 * without failures is marked in settings so later app starts skip it; failures leave the marker unset
 * and are retried. The key is versioned ({@code ...V2}) because the first release only cleaned up
 * collections with no live responsible, so installations that already ran it must run again.
 * <p>
 * One-shot because V1_352 is the bulk of the drift and it cannot recur. Note that it is not the only
 * source: {@code it_system_attestation_responsible.user_uuid} cascades on user deletion (V1_351),
 * while the collection uuids do not, so {@code CleanupDeletedUsersTask} silently shrinks the live list
 * every week and can leave a system with no responsible at all behind a non-empty collection. Those no
 * longer generate mails - recipients are looked up with findByUuidInAndDeletedFalse - but they do
 * reappear in the attestation admin overview, so consider making this periodic rather than deleting it
 * outright. If it does go, take with it, unless used elsewhere:
 * {@link ItSystemService#reconcileAttestationResponsibleCollection(long)},
 * {@link AttestationResponsibleCollectionDao#findAllItSystemIds()} and the settings key with its
 * getter/setter in {@code SettingsService}.
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
	@WarnIfSlowerThan(millis = 10 * 1000)
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
		// Per-system transaction via @Transactional on the service method: one bad system must not
		// roll back the whole pass.
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
			// Only mark as done on a clean pass - failed systems need another attempt on the next app
			// start, where the marker is still absent.
			settingsService.setAttestationResponsibleCollectionReconcilePerformed();
		}
		done = true;
	}
}
