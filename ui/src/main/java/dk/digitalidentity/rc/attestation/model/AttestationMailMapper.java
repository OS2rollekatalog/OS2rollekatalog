package dk.digitalidentity.rc.attestation.model;

import dk.digitalidentity.rc.attestation.model.dto.AttestationSentMailDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationSentMailReceiverDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMailReceiver;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;


@Component
@RequiredArgsConstructor
public class AttestationMailMapper {
    private final AttestationCachedUserService cachedUserService;
    private final MessageSource messageSource;


    public List<AttestationSentMailDTO> sentMail(final Attestation attestation) {
        return attestation.getMails().stream()
                .map(m -> AttestationSentMailDTO.builder()
                        .sentAt(m.getSentAt())
                        .template(emailTemplateType(m.getEmailTemplateType()))
                        .receivers(sentMailReceivers(m.getReceivers()))
                        .build())
                .toList();
    }

    private List<AttestationSentMailReceiverDTO> sentMailReceivers(final List<AttestationMailReceiver> receivers) {
        return receivers.stream()
                .map(r -> AttestationSentMailReceiverDTO.builder()
                        .cc(r.getReceiverType() == AttestationMailReceiver.ReceiverType.CC)
                        .email(r.getEmail())
                        .userName(cachedUserService.userNameFromUuidCached(r.getUserUuid()))
                        .message(r.getMessage())
                        .title(r.getTitle())
                        .build())
                .toList();
    }

    private String emailTemplateType(final EmailTemplateType type) {
        final String messageLookupKey = switch (type) {
            case ATTESTATION_NOTIFICATION -> "overview.enum.email.message.type.attestation_notification";
            case ATTESTATION_REMINDER1 -> "overview.enum.email.message.type.attestation_reminder1";
            case ATTESTATION_REMINDER2 -> "overview.enum.email.message.type.attestation_reminder2";
            case ATTESTATION_REMINDER3 -> "overview.enum.email.message.type.attestation_reminder3";
            case ATTESTATION_REMINDER_THIRDPARTY -> "overview.enum.email.message.type.attestation_reminder_thirdparty";
            case ATTESTATION_SENSITIVE_NOTIFICATION -> "overview.enum.email.message.type.attestation_sensitive_notification";
            case ATTESTATION_SENSITIVE_REMINDER1 -> "overview.enum.email.message.type.attestation_sensitive_reminder1";
            case ATTESTATION_SENSITIVE_REMINDER2 -> "overview.enum.email.message.type.attestation_sensitive_reminder2";
            case ATTESTATION_SENSITIVE_REMINDER3 -> "overview.enum.email.message.type.attestation_sensitive_reminder3";
            case ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY -> "overview.enum.email.message.type.attestation_sensitive_reminder_thirdparty";
            case ATTESTATION_MANAGERDELEGATE_NOTIFICATION -> "html.enum.email.message.type.managerdelegate.attestation_notification";
            case ATTESTATION_MANAGERDELEGATE_REMINDER2-> "html.enum.email.message.type.managerdelegate.attestation_reminder2";
            case ATTESTATION_MANAGERDELEGATE_REMINDER3-> "html.enum.email.message.type.managerdelegate.attestation_reminder3";
            case ATTESTATION_MANAGERDELEGATE_REMINDER_THIRDPARTY-> "html.enum.email.message.type.managerdelegate.attestation_reminder_thirdparty";
            case ATTESTATION_IT_SYSTEM_NOTIFICATION -> "overview.enum.email.message.type.attestation_it_system_notification";
            case ATTESTATION_IT_SYSTEM_REMINDER1 -> "overview.enum.email.message.type.attestation_it_system_reminder1";
            case ATTESTATION_IT_SYSTEM_REMINDER2 -> "overview.enum.email.message.type.attestation_it_system_reminder2";
            case ATTESTATION_IT_SYSTEM_REMINDER3 -> "overview.enum.email.message.type.attestation_it_system_reminder3";
            case ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY -> "overview.enum.email.message.type.attestation_it_system_reminder_thirdparty";
            case ATTESTATION_IT_SYSTEM_ASSIGNMENT_NOTIFICATION -> "overview.enum.email.message.type.attestation_it_system_assignment_notification";
            case ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER1 -> "overview.enum.email.message.type.attestation_it_system_assignment_reminder1";
            case ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER2 -> "overview.enum.email.message.type.attestation_it_system_assignment_reminder2";
            case ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER3 -> "overview.enum.email.message.type.attestation_it_system_assignment_reminder3";
            case ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY -> "overview.enum.email.message.type.attestation_it_system_assignment_reminder_thirdparty";
            default -> "";
        };
        return messageSource.getMessage(messageLookupKey, null, Locale.ENGLISH);
    }

}
