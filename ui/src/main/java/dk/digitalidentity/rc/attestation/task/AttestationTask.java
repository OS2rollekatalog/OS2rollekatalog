package dk.digitalidentity.rc.attestation.task;

import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.service.temporal.OuAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.temporal.SystemRoleAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.temporal.UserAssignmentsUpdaterJdbc;
import dk.digitalidentity.rc.attestation.service.tracker.ItSystemAttestationTrackerService;
import dk.digitalidentity.rc.attestation.service.tracker.UserAttestationTrackerService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
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
    private SettingsService settingsService;

    @Autowired
    private RoleCatalogueConfiguration configuration;

    @Timed(longTask = true, value = "attestation.attestation_task.timer")
    @Scheduled(cron = "${rc.attestation.attestation_cron}")
    public void updateAttestation() {
        if (!configuration.getScheduled().isEnabled()) {
            return;
        }
        
        if (!settingsService.isScheduledAttestationEnabled()) {
            log.debug("Attestation not enabled, no tracking needed");
            return;
        }
        
        log.info("Attestation tracker running");

        final LocalDate now = LocalDate.now();
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
        userAttestationTracker.updateSystemUserAttestations(now);
        userAttestationTracker.updateOrganisationUserAttestations(now);
        systemAttestationTracker.updateItSystemRolesAttestations(now);
    }

    private void updateAssignments(LocalDate now) {
        userAssignmentsUpdaterJdbc.updateUserRoleAssignments(now);
        ouAssignmentsUpdaterJdbc.updateOuAssignments(now);
        systemRoleAssignmentsUpdaterJdbc.updateItSystemAssignments(now);
    }

}
