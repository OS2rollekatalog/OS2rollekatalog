package dk.digitalidentity.rc.attestation.task;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.attestation.service.ItSystemUsersAttestationService;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.attestation.service.temporal.OuAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.temporal.SystemRoleAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.temporal.UserAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.tracker.AttestationRunTrackerService;
import dk.digitalidentity.rc.attestation.service.tracker.ItSystemAttestationTrackerService;
import dk.digitalidentity.rc.attestation.service.tracker.UserAttestationTrackerService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.log.WarnIfSlowerThan;
import dk.digitalidentity.rc.service.HistoryService;
import dk.digitalidentity.rc.service.SettingsService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class AttestationTask {

	@Autowired
    private ItSystemAttestationTrackerService systemAttestationTracker;

    @Autowired
    private UserAttestationTrackerService userAttestationTracker;

    @Autowired
    private AttestationRunTrackerService attestationRunTrackerService;

    @Autowired
    private SystemRoleAssignmentsUpdaterJdbc systemRoleAssignmentsUpdaterJdbc;

    @Autowired
    private OuAssignmentsUpdaterJdbc ouAssignmentsUpdaterJdbc;

    @Autowired
    private OrganisationAttestationService organisationAttestationService;

    @Autowired
    private ItSystemUsersAttestationService itSystemUsersAttestationService;

    @Autowired
    private ItSystemUserRolesAttestationService itSystemUserRolesAttestationService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private RoleCatalogueConfiguration configuration;

    @Autowired
    private HistoryService historyService;

	@Autowired
	private UserAssignmentsUpdaterJdbc userAssignmentsUpdaterJdbc;

	@Timed(longTask = true, value = "attestation.finish_outstanding_task.timer")
    @Scheduled(cron = "${rc.attestation.finish_outstanding_cron:#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 6 * * *}")
	@WarnIfSlowerThan(millis = 1 * 60 * 1000)
    public void finishOutstandingAttestations() {
        // This task will look through unfinished attestestations and finish them in case they are done
        if (!configuration.getScheduled().isEnabled()) {
            return;
        }

        if (!settingsService.isScheduledAttestationEnabled()) {
            log.debug("Attestation not enabled, no tracking needed");
            return;
        }
        log.info("Attestation finish outstanding running");

        organisationAttestationService.finishOutstandingAttestations();
        itSystemUsersAttestationService.finishOutstandingAttestations();
        itSystemUserRolesAttestationService.finishOutstandingAttestations();

        log.info("Attestation finish outstanding done");

    }

    @Timed(longTask = true, value = "attestation.attestation_task.timer")
    @Scheduled(cron = "${rc.attestation.attestation_cron:#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 5 * * *}")
    @WarnIfSlowerThan(millis = 3 * 60 * 1000)
	public void updateAttestation() {
        if (!configuration.getScheduled().isEnabled()) {
            return;
        }

        if (!settingsService.isScheduledAttestationEnabled()) {
            log.debug("Attestation not enabled, no tracking needed");
            return;
        }
        final LocalDate now = LocalDate.now();
        if (!historyService.hasHistoryBeenGenerated(now)) {
            log.error("Will not update attestations, when history is missing");
            return;
        }

        log.info("Attestation tracker running");

        try {
            final LocalDate lastRun = settingsService.getScheduledAttestationLastRun().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            if (!now.isAfter(lastRun)) {
                throw new AttestationDataUpdaterException("Cannot update attestation more than once pr. day");
            }

            updateAssignments(now);
            updateTrackers(now);
            settingsService.setScheduledAttestationLastRun(new Date());

            log.info("Attestation tracker done");
        }
        catch (final AttestationDataUpdaterException e) {
            log.info("Failed to update attestations: " + e.getMessage());
        }
        catch (final Exception e) {
            log.error("Failed to update attestations", e);
        }
    }

    private void updateTrackers(LocalDate now) throws Exception {
    	log.info("starting migrateAttestationsWithoutRun");
        attestationRunTrackerService.migrateAttestationsWithoutRun();
        log.info("starting updateRuns");
        attestationRunTrackerService.updateRuns(now);
        log.info("starting updateSystemUserAttestations");
        userAttestationTracker.updateSystemUserAttestations(now);
        log.info("starting updateOrganisationUserAttestations");
        userAttestationTracker.updateOrganisationUserAttestations(now);
        log.info("starting updateItSystemRolesAttestations");
        systemAttestationTracker.updateItSystemRolesAttestations(now);
    }

    private void updateAssignments(LocalDate now) {
    	log.info("starting updateOuAssignments");
        ouAssignmentsUpdaterJdbc.updateOuAssignments(now);
        log.info("starting updateUserRoleAssignments");
		userAssignmentsUpdaterJdbc.updateUserRoleAssignments(now);
		log.info("starting updateItSystemAssignments");
        systemRoleAssignmentsUpdaterJdbc.updateItSystemAssignments(now);
    }
}
