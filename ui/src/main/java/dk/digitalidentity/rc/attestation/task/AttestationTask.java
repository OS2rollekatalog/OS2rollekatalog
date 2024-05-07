package dk.digitalidentity.rc.attestation.task;

import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.attestation.service.ItSystemUsersAttestationService;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.attestation.service.temporal.OuAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.temporal.SystemRoleAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.temporal.UserAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.tracker.ItSystemAttestationTrackerService;
import dk.digitalidentity.rc.attestation.service.tracker.UserAttestationTrackerService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.HistoryService;
import dk.digitalidentity.rc.service.SettingsService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
@EnableScheduling
public class AttestationTask {

	@Autowired
    private ItSystemAttestationTrackerService systemAttestationTracker;

    @Autowired
    private UserAttestationTrackerService userAttestationTracker;

    @Autowired
    private SystemRoleAssignmentsUpdaterJdbc systemRoleAssignmentsUpdaterJdbc;

    @Autowired
    private UserAssignmentsUpdaterJdbc userAssignmentsUpdaterJdbc;

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

    @Timed(longTask = true, value = "attestation.finish_outstanding_task.timer")
    @Scheduled(cron = "${rc.attestation.finish_outstanding_cron}")
//    @Scheduled(fixedDelay = 10000000L)
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
    @Scheduled(cron = "${rc.attestation.attestation_cron}")
//    @Scheduled(fixedDelay = 10000000L)
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

    private void updateTrackers(LocalDate now) {
        final LocalDate deadline = findNextAttestationDate(now);
        if (configuration.getAttestation().getAlwaysRunTracker()
                || deadline.isEqual(now.plusDays(configuration.getAttestation().getDaysForAttestation()))) {
            // Only update attestations exactly DaysForAttestation before deadline
            userAttestationTracker.updateSystemUserAttestations(now);
            userAttestationTracker.updateOrganisationUserAttestations(now);
            systemAttestationTracker.updateItSystemRolesAttestations(now);
        }
    }

    private void updateAssignments(LocalDate now) {
        userAssignmentsUpdaterJdbc.updateUserRoleAssignments(now);
        ouAssignmentsUpdaterJdbc.updateOuAssignments(now);
        systemRoleAssignmentsUpdaterJdbc.updateItSystemAssignments(now);
    }

    private LocalDate findNextAttestationDate(final LocalDate when) {
        final CheckupIntervalEnum interval = settingsService.getScheduledAttestationInterval();
        LocalDate deadline = settingsService.getFirstAttestationDate();
        while (deadline.isBefore(when)) {
            deadline = deadline.plusMonths(intervalToMonths(interval));
        }
        return deadline;
    }

    // NOTE: Interval is halfed since we need to run on sensitive attestations also.
    private static int intervalToMonths(final CheckupIntervalEnum intervalEnum) {
        return switch (intervalEnum) {
            case YEARLY -> 6;
            case EVERY_HALF_YEAR -> 3;
        };
    }

}
