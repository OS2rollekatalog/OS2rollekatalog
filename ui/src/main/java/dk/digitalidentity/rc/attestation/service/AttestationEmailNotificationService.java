package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationMailDao;
import dk.digitalidentity.rc.attestation.exception.AttestationEmailNotificationException;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Component
public class AttestationEmailNotificationService {
    @Autowired
    private RoleCatalogueConfiguration configuration;

    @Autowired
    private AttestationDao attestationDao;

    @Autowired
    private AttestationEmailNotificationService self;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private EmailQueueService emailQueueService;

    @Autowired
    private AttestationMailDao attestationMailDao;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private OrgUnitDao orgUnitDao;


    public void sendRequestForAdRemoval(final String requester, final String user) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_REMOVAL);
        final String message = resolveRequestChangeMailMessage(requester, user, null, template, null);
        final String title = resolveRequestChangeMailTitle(requester, user, null, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for deletion notification (leder=" + requester + ", user=" + user + ")");
        emailQueueService.queueEmail(email, title, message, template, null);
    }


    public void sendRequestForChangeMail(final String requester, final String user, final String change, List<RoleAssignmentDTO> notApproved) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE);
        final String message = resolveRequestChangeMailMessage(requester, user, change, template, notApproved);
        final String title = resolveRequestChangeMailTitle(requester, user, null, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for change notification (leder=" + requester + ", change=" + change + ")");
        emailQueueService.queueEmail(email, title, message, template, null);
    }

    public void sendRequestForRoleChange(final String requester, final String userRole, final String itSystem) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_ROLE_CHANGE);
        final String message = resolveRequestForRoleChangeMailMessage(requester, userRole, itSystem, template);
        final String title = resolveRequestForRoleChangeMailTitle(requester, userRole, itSystem, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for role change notification (leder=" + requester + ", it-system=" + itSystem + ", userRole=" + userRole + ")");
        emailQueueService.queueEmail(email, title, message, template, null);
    }

    public void sendItSystemNotifications(final LocalDate now) {
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.INFORMATION, now, configuration.getAttestation().getNotifyDaysBeforeDeadline());
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.REMINDER_1, now, configuration.getAttestation().getReminder1DaysBeforeDeadline());
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.REMINDER_2, now, configuration.getAttestation().getReminder2DaysBeforeDeadline());
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.ESCALATION_REMINDER, now, -configuration.getAttestation().getEscalationReminderDaysAfterDeadline());
    }

    public void sendOrganisationNotifications(final LocalDate now) {
        sendEmails(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.INFORMATION, now, configuration.getAttestation().getNotifyDaysBeforeDeadline());
        sendEmails(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.REMINDER_1, now, configuration.getAttestation().getReminder1DaysBeforeDeadline());
        sendEmails(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.REMINDER_2, now, configuration.getAttestation().getReminder2DaysBeforeDeadline());
        sendEmails(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.ESCALATION_REMINDER, now, -configuration.getAttestation().getEscalationReminderDaysAfterDeadline());
    }

    private void sendEmails(final Attestation.AttestationType attestationType, final AttestationMail.MailType mailType,
                            final LocalDate now, final Integer daysBefore) {
        final List<Attestation> attestations = attestationDao.findAttestationsWhichNeedsMail(attestationType, mailType, now.plusDays(daysBefore));
        attestations.forEach(a -> self.sendEmail(a.getId(), attestationType, mailType));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEmail(final Long attestationId, final Attestation.AttestationType attestationType,  AttestationMail.MailType mailType) {
        final Attestation attestation = attestationDao.findById(attestationId)
                .orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, attestation with id %d not found", attestationId));
        final EmailTemplate template = sensitiveOnly(attestation)
                ? findSensitiveEmailTemplate(mailType)
                : findEmailTemplate(attestationType, mailType);
        final User systemResponsible = attestation.getResponsibleUserUuid() != null ? userDao.findByUuidAndDeletedFalse(attestation.getResponsibleUserUuid()) : null;
        if (template.isEnabled()) {
            final List<User> targetUsers = findTargetUsers(attestation, mailType == AttestationMail.MailType.ESCALATION_REMINDER);
            if (targetUsers == null) {
                log.warn("No target users was found for attestation with id: " + attestation.getId());
                return;
            }
            targetUsers.forEach(receiver -> {
                final String message = resolveMessage(attestation, receiver, systemResponsible, template);
                final String title = resolveTitle(attestation, receiver, systemResponsible, template);
                final String email = receiver.getEmail();
                log.info("Sending attestation notification (" + attestation.getItSystemName() + ", " + template.getTemplateType().name() + ", " + mailType.name() + " to " + email + ")");
                emailQueueService.queueEmail(email, title, message, template, null);
            });
            attestation.getMails()
                    .add(attestationMailDao.save(AttestationMail.builder()
                            .attestation(attestation)
                            .emailTemplateType(template.getTemplateType())
                            .emailType(mailType)
                            .sentAt(ZonedDateTime.now())
                            .build()));
        } else {
            log.info(template.getTitle() + " is disabled, notification not sent for attestation: " + attestation.getUuid());
        }
    }

    private List<User> findTargetUsers(final Attestation attestation, final boolean escalation) {
        boolean systemEscalation = escalation && attestation.getResponsibleUserUuid() != null;
        User responsibleUser = null;
        if (attestation.getResponsibleUserUuid() != null) {
            responsibleUser = userDao.findByUuidAndDeletedFalse(attestation.getResponsibleUserUuid());
        }
        if (responsibleUser == null && attestation.getResponsibleOuUuid() != null) {
            responsibleUser = orgUnitDao.findById(attestation.getResponsibleOuUuid())
                    .map(OrgUnit::getManager)
                    .orElse(null);
        }

        if (systemEscalation && responsibleUser != null) {
            return responsibleUser.getPositions().stream()
                    .map(Position::getOrgUnit)
                    .map(OrgUnit::getManager)
                    .filter(Objects::nonNull)
                    .findFirst().map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        } else if (escalation && responsibleUser != null) {
            final User fResponsibleUser = responsibleUser;
            // Find the manager's manager(s)
            return responsibleUser.getPositions().stream()
                    .map(Position::getOrgUnit)
                    .filter(ou -> ou.getManager() != null
                            && ou.getManager().getUuid().equals(fResponsibleUser.getUuid()))
                    .flatMap(ou -> findManagersManager(fResponsibleUser, ou, 0).stream())
                    .collect(Collectors.toList());
        } else if (responsibleUser != null) {
            if (attestation.getAttestationType() == Attestation.AttestationType.ORGANISATION_ATTESTATION) {
                // Find substitutes(stedfortrædere)
                final List<User> substitutes = orgUnitDao.findById(attestation.getResponsibleOuUuid())
                        .stream()
                        .map(OrgUnit::getManager)
                        .filter(Objects::nonNull)
                        .flatMap(manager -> manager.getManagerSubstitutes().stream())
                        .filter(s -> s.getOrgUnit().getUuid().equals(attestation.getResponsibleOuUuid()))
                        .map(ManagerSubstitute::getSubstitute)
                        .toList();
                return Stream.concat(Stream.of(responsibleUser), substitutes.stream())
                        .collect(Collectors.toList());
            } else {
                return Collections.singletonList(responsibleUser);
            }
        }
        return null;
    }

    private List<User> findManagersManager(final User manager, final OrgUnit ou, int depth) {
        if (depth > 10) {
            // In case the OU hierarchy have circular structure, bail out here
            return Collections.emptyList();
        }
        if (ou != null && ou.getParent() != null) {
            if (ou.getManager() != null && !ou.getManager().getUuid().equals(manager.getUuid())) {
                return List.of(ou.getManager());
            } else {
                return findManagersManager(manager, ou.getParent(), ++depth);
            }
        }
        return Collections.emptyList();
    }

    private String resolveTitle(Attestation attestation, User user, User systemResponsible, EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            title = StringUtils.replace(title, placeholder.getPlaceholder(), placeholderValue(attestation, user, systemResponsible, placeholder));
        }
        return title;
    }

    private String resolveMessage(final Attestation attestation, final User user, final User systemResponsible, final EmailTemplate template) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = StringUtils.replace(message, placeholder.getPlaceholder(), placeholderValue(attestation, user, systemResponsible, placeholder));
        }
        return message;
    }


    private String resolveRequestChangeMailTitle(final String requester, final String user, final String change, final EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            title = StringUtils.replace(title, placeholder.getPlaceholder(), placeholderValue(requester, change, user, null, null, null, placeholder));
        }
        return title;
    }

    private String resolveRequestChangeMailMessage(final String requester, final String user, final String change, final EmailTemplate template, List<RoleAssignmentDTO> notApproved) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = StringUtils.replace(message, placeholder.getPlaceholder(), placeholderValue(requester, change, user, null, null, notApproved, placeholder));
        }
        return message;
    }


    private String resolveRequestForRoleChangeMailTitle(final String requester, final String role, final String itSystem, final EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            title = StringUtils.replace(title, placeholder.getPlaceholder(), placeholderValue(requester, null, null, role, itSystem, null, placeholder));
        }
        return title;
    }

    private String resolveRequestForRoleChangeMailMessage(final String requester, final String role, final String itSystem, final EmailTemplate template) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = StringUtils.replace(message, placeholder.getPlaceholder(), placeholderValue(requester, null, null, role, itSystem, null, placeholder));
        }
        return message;
    }

    private String placeholderValue(final Attestation attestation, final User user, final User systemResponsible, final EmailTemplatePlaceholder placeholder) {
        return switch (placeholder) {
            case RECEIVER_PLACEHOLDER -> user != null ? user.getName() : "ukendt";
            case ORGUNIT_PLACEHOLDER -> findOrgUnitName(attestation);
            case ITSYSTEM_PLACEHOLDER -> attestation.getItSystemName();
            case SYSTEM_RESPONSIBLE_PLACEHOLDER -> systemResponsible != null ? systemResponsible.getName() : "ukendt";
            default -> {
                log.warn(placeholder.getPlaceholder() + " is not resolved in AttestationEmailNotificationService");
                yield placeholder.getPlaceholder();
            }
        };
    }

    private String placeholderValue(final String requester, final String change, final String user, final String role, final String itSystem, List<RoleAssignmentDTO> notApproved,
                                    final EmailTemplatePlaceholder placeholder) {
        return switch (placeholder) {
            case CHANGE_REQUESTED_PLACEHOLDER -> change != null ? change : placeholder.getPlaceholder();
            case REQUESTER_PLACEHOLDER -> requester;
            case USER_PLACEHOLDER -> user != null ? user : placeholder.getPlaceholder();
            case ROLE_NAME -> role != null ? role : placeholder.getPlaceholder();
            case ITSYSTEM_PLACEHOLDER -> itSystem != null ? itSystem : placeholder.getPlaceholder();
            case LIST_OF_CHANGE_REQUESTS -> notApproved == null ? "" : getChangeList(notApproved);
            default -> {
                log.warn(placeholder.getPlaceholder() + " is not resolved in AttestationEmailNotificationService");
                yield placeholder.getPlaceholder();
            }
        };
    }

    private String getChangeList(List<RoleAssignmentDTO> notApproved) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Følgende rolletildelinger ønskes ændret: <br/>");
        stringBuilder.append("<ul>");
        for (RoleAssignmentDTO dto : notApproved) {
            stringBuilder.append("<li>");
            stringBuilder.append(dto.getRoleType().equals(RoleType.ROLEGROUP) ? "Rollebuketten " : "Jobfunktionsrollen ");
            stringBuilder.append(dto.getRoleName());
            stringBuilder.append(" med id ");
            stringBuilder.append(dto.getRoleId());
            stringBuilder.append("</li>");
        }
        stringBuilder.append("</ul>");
        return stringBuilder.toString();
    }

    private String findOrgUnitName(final Attestation attestation) {
        if (attestation.getResponsibleOuUuid() != null) {
            return orgUnitDao.findById(attestation.getResponsibleOuUuid()).map(OrgUnit::getName).orElse(null);
        }
        if (attestation.getResponsibleUserUuid() != null) {
            return userDao.findByUuidAndDeletedFalse(attestation.getResponsibleUserUuid())
                    .getPositions().stream().map(Position::getOrgUnit)
                    .filter(Objects::nonNull)
                    .map(OrgUnit::getName)
                    .collect(Collectors.joining(","));
        }
        return "Ukendt";
    }

    private EmailTemplate findEmailTemplate(final Attestation.AttestationType attestationType,  AttestationMail.MailType mailType) {
        return switch (mailType) {
            case INFORMATION -> attestationType == Attestation.AttestationType.ORGANISATION_ATTESTATION
                    ? emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION)
                    : emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION);
            case REMINDER_1 -> attestationType == Attestation.AttestationType.ORGANISATION_ATTESTATION
                    ? emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_10_DAYS)
                    : emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER_10_DAYS);
            case REMINDER_2 -> attestationType == Attestation.AttestationType.ORGANISATION_ATTESTATION
                    ? emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_3_DAYS)
                    : emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER_3_DAYS);
            case ESCALATION_REMINDER -> attestationType == Attestation.AttestationType.ORGANISATION_ATTESTATION
                    ? emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY)
                    : emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY);
        };
    }

    private EmailTemplate findSensitiveEmailTemplate(final AttestationMail.MailType mailType) {
        return switch (mailType) {
            case INFORMATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_NOTIFICATION);
            case REMINDER_1 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER_10_DAYS);
            case REMINDER_2 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER_3_DAYS);
            case ESCALATION_REMINDER -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY);
        };
    }

    private static boolean sensitiveOnly(final Attestation attestation) {
        if (attestation.getUsersForAttestation().size() == 0) {
            return false;
        }
        return attestation.getUsersForAttestation().stream()
                .allMatch(AttestationUser::isSensitiveRoles);
    }

}
