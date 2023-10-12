package dk.digitalidentity.rc.attestation.task;


import dk.digitalidentity.rc.attestation.service.AttestationEmailNotificationService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.SettingsService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;


@Slf4j
@Component
@EnableScheduling
public class AttestationEmailNotificationTask {
    @Autowired
    private AttestationEmailNotificationService attestationEmailNotificationService;
    @Autowired
    private RoleCatalogueConfiguration configuration;
    @Autowired
    private SettingsService settingsService;

    @Timed(longTask = true, value = "attestation.attestation_email_notification_task.timer")
    @Scheduled(cron = "${rc.attestation.attestation_notifications_cron}")
    public void notifyManagers() {
        if (!configuration.getScheduled().isEnabled()) {
            return;
        }
        if (!settingsService.isScheduledAttestationEnabled()) {
            log.info("Attestation not enabled, no tracking needed");
            return;
        }
        try {
            final LocalDate now = LocalDate.now();
            attestationEmailNotificationService.sendItSystemNotifications(now);
            attestationEmailNotificationService.sendOrganisationNotifications(now);
        } catch (final Exception exception) {
            log.error("Nofication failed", exception);
        }
    }

}
