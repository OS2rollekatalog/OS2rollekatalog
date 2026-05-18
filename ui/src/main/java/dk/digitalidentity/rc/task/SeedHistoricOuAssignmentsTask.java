package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
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
 * Engangs-seed af {@code historic_ou_assignment} for eksisterende OU-tildelinger.
 * <p>
 * Søsterklasse til {@link SeedHistoricItSystemAssignmentsTask}. Tabellen blev introduceret
 * i V1_335 og populeres udelukkende event-drevet via {@code UpdatedAssignmentCalculatorHook}.
 * Eksisterende OU-tildelinger (både {@code ou_roles} og {@code ou_rolegroups}) har derfor
 * ingen historic-række efter deploy før en admin tilfældigvis rører dem — hvilket gør at
 * deres attesteringer mangler rollebuketter, enhedstildelinger og udtrækninger.
 * <p>
 * Hver tick behandler én chunk fra hver af de to kilde-tabeller; hver assignment seedes i
 * sin egen tx via service-metoden, så én dårlig række ikke ruller hele chunk'en tilbage.
 * Engangs-natur: tasken stopper når begge køer er tomme og forbliver stille indtil næste
 * app-start.
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class SeedHistoricOuAssignmentsTask {

	private static final int CHUNK_SIZE = 200;

	private final RoleCatalogueConfiguration configuration;
	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	private final HistoricOuAssignmentService historicOuAssignmentService;

	private volatile boolean userRoleDone = false;
	private volatile boolean roleGroupDone = false;
	private boolean started = false;
	private long totalUserRoleSeeded = 0;
	private long totalRoleGroupSeeded = 0;
	// Assignment-IDs der konsistent fejler. Holdes i hukommelsen så tasken ikke spinner evigt
	// på de samme rækker — query'en returnerer dem hver tick (de har stadig ingen åben
	// historic-række), men vi springer dem over efter første fejl. Genstart resetter — så
	// transiente fejl får et nyt forsøg.
	private final Set<Long> permanentlyFailedUserRole = new HashSet<>();
	private final Set<Long> permanentlyFailedRoleGroup = new HashSet<>();

	@Scheduled(fixedDelay = 30_000)
	public void seedChunk() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		if (userRoleDone && roleGroupDone) {
			return;
		}

		if (!userRoleDone) {
			userRoleDone = processUserRoleChunk();
		}
		if (!roleGroupDone) {
			roleGroupDone = processRoleGroupChunk();
		}

		if (userRoleDone && roleGroupDone && started) {
			log.info("Seed of historic_ou_assignment complete: {} ou_roles + {} ou_rolegroups seeded in total ({} + {} permanently failed)",
				totalUserRoleSeeded, totalRoleGroupSeeded,
				permanentlyFailedUserRole.size(), permanentlyFailedRoleGroup.size());
		}
	}

	/** @return true hvis ou_roles-køen er tom (alle resterende IDs er permanent-failed). */
	private boolean processUserRoleChunk() {
		List<Long> ids = orgUnitUserRoleAssignmentDao.findIdsMissingOpenHistoricRow(PageRequest.of(0, CHUNK_SIZE));
		List<Long> processable = ids.stream().filter(id -> !permanentlyFailedUserRole.contains(id)).toList();
		if (processable.isEmpty()) {
			return true;
		}
		markStarted();

		int seeded = 0;
		int failed = 0;
		// Bevidst per-item transaktion via @Transactional på service-metoden frem for én chunk-tx:
		// hvis én assignment fejler (lazy-load NPE, FK-overtrædelse osv.) skal vi ikke rulle hele
		// chunk'en tilbage og hænge fast i den samme 200-er batch ved næste tick.
		for (Long id : processable) {
			try {
				if (historicOuAssignmentService.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(id)) {
					seeded++;
				}
			} catch (Exception e) {
				failed++;
				permanentlyFailedUserRole.add(id);
				log.warn("Failed to seed historic_ou_assignment for OrgUnitUserRoleAssignment id={} — skipping on subsequent ticks", id, e);
			}
		}
		totalUserRoleSeeded += seeded;
		if (failed > 0) {
			log.info("Seeded chunk of {} ou_roles historic rows ({} failed) (running total: {})", seeded, failed, totalUserRoleSeeded);
		} else {
			log.info("Seeded chunk of {} ou_roles historic rows (running total: {})", seeded, totalUserRoleSeeded);
		}
		return false;
	}

	/** @return true hvis ou_rolegroups-køen er tom (alle resterende IDs er permanent-failed). */
	private boolean processRoleGroupChunk() {
		List<Long> ids = orgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow(PageRequest.of(0, CHUNK_SIZE));
		List<Long> processable = ids.stream().filter(id -> !permanentlyFailedRoleGroup.contains(id)).toList();
		if (processable.isEmpty()) {
			return true;
		}
		markStarted();

		int seeded = 0;
		int failed = 0;
		for (Long id : processable) {
			try {
				if (historicOuAssignmentService.seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(id)) {
					seeded++;
				}
			} catch (Exception e) {
				failed++;
				permanentlyFailedRoleGroup.add(id);
				log.warn("Failed to seed historic_ou_assignment for OrgUnitRoleGroupAssignment id={} — skipping on subsequent ticks", id, e);
			}
		}
		totalRoleGroupSeeded += seeded;
		if (failed > 0) {
			log.info("Seeded chunk of {} ou_rolegroups historic rows ({} failed) (running total: {})", seeded, failed, totalRoleGroupSeeded);
		} else {
			log.info("Seeded chunk of {} ou_rolegroups historic rows (running total: {})", seeded, totalRoleGroupSeeded);
		}
		return false;
	}

	private void markStarted() {
		if (!started) {
			log.info("Starting one-shot seed of historic_ou_assignment for existing OU role/role-group assignments");
			started = true;
		}
	}
}
