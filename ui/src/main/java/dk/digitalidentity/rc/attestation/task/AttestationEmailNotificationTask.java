package dk.digitalidentity.rc.attestation.task;


import dk.digitalidentity.rc.attestation.service.AttestationEmailNotificationService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired
    private EmailTemplateService emailTemplateService;

    @Timed(longTask = true, value = "attestation.attestation_email_notification_task.timer")
    @Scheduled(cron = "${rc.attestation.attestation_notifications_cron}")
//    @Scheduled(fixedDelay = 50000000)
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

    // TODO KBP 24/8-2024 Remove this in a future release, as this only needs to be run once
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateEmailDaysBefore() {
        emailTemplateService.findAll().stream()
                .filter(t -> t.getTemplateType().isAllowDaysBeforeDeadline() && t.getDaysBeforeEvent() == null)
                .forEach(this::setTemplateDaysBeforeEventFromDefaults);
    }

    private void setTemplateDaysBeforeEventFromDefaults(final EmailTemplate template) {
        final Integer daysBefore = switch (template.getTemplateType()) {
            case ATTESTATION_NOTIFICATION, ATTESTATION_SENSITIVE_NOTIFICATION, ATTESTATION_IT_SYSTEM_NOTIFICATION, ATTESTATION_IT_SYSTEM_ASSIGNMENT_NOTIFICATION -> configuration.getAttestation().getNotifyDaysBeforeDeadline();
            case ATTESTATION_REMINDER1, ATTESTATION_SENSITIVE_REMINDER1, ATTESTATION_IT_SYSTEM_REMINDER1, ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER1 -> configuration.getAttestation().getReminder1DaysBeforeDeadline();
            case ATTESTATION_REMINDER2, ATTESTATION_SENSITIVE_REMINDER2, ATTESTATION_IT_SYSTEM_REMINDER2, ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER2 -> configuration.getAttestation().getReminder2DaysBeforeDeadline();
            case ATTESTATION_REMINDER3, ATTESTATION_SENSITIVE_REMINDER3, ATTESTATION_IT_SYSTEM_REMINDER3, ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER3 -> -configuration.getAttestation().getReminder3DaysAfterDeadline();
            case ATTESTATION_REMINDER_THIRDPARTY, ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY, ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY, ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY -> -configuration.getAttestation().getEscalationReminderDaysAfterDeadline();
            default -> throw new IllegalArgumentException("Unexpected value: " + template.getTemplateType());
        };
        template.setDaysBeforeEvent(daysBefore);
    }


}
