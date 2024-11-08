package dk.digitalidentity.rc.attestation.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mysema.commons.lang.Pair;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationMailDao;
import dk.digitalidentity.rc.attestation.exception.AttestationEmailNotificationException;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMailReceiver;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
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


@Slf4j
@Component
public class AttestationEmailNotificationService {

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

    @Autowired
    private AttestationRunService attestationRunService;

    @Autowired
    private ItSystemDao itSystemDao;


    public void sendRequestForAdRemoval(final String requester, final String user, final String responsibleOu) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_REMOVAL);
        final String message = resolveRequestChangeMailMessage(requester, user, null, template, null, responsibleOu);
        final String title = resolveRequestChangeMailTitle(requester, user, null, responsibleOu, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for deletion notification (leder=" + requester + ", user=" + user + ")");
        emailQueueService.queueEmail(email, title, message, template, null, null);
    }


    public void sendRequestForChangeMail(final String requester, final String user, final String change, List<RoleAssignmentDTO> notApproved) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE);
        final String message = resolveRequestChangeMailMessage(requester, user, change, template, notApproved, null);
        final String title = resolveRequestChangeMailTitle(requester, user, null, null, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for change notification (leder=" + requester + ", change=" + change + ")");
        emailQueueService.queueEmail(email, title, message, template, null, null);
    }

    public void sendRequestForRoleChange(final String requester, final String userRole, final String itSystem, final String remark) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_ROLE_CHANGE);
        final String message = resolveRequestForRoleChangeMailMessage(requester, userRole, itSystem, remark, template);
        final String title = resolveRequestForRoleChangeMailTitle(requester, userRole, itSystem, remark, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for role change notification (leder=" + requester + ", it-system=" + itSystem + ", userRole=" + userRole + ")");
        emailQueueService.queueEmail(email, title, message, template, null, null);
    }

    @Transactional
    public void sendItSystemNotifications(final LocalDate now) {
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.INFORMATION, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.REMINDER_1, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.REMINDER_2, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.REMINDER_3, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.ESCALATION_REMINDER, now);

        sendEmails(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, AttestationMail.MailType.INFORMATION, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, AttestationMail.MailType.REMINDER_1, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, AttestationMail.MailType.REMINDER_2, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, AttestationMail.MailType.REMINDER_3, now);
        sendEmails(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, AttestationMail.MailType.ESCALATION_REMINDER, now);
    }

    @Transactional
    public void sendOrganisationNotifications(final LocalDate now) {
        sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.INFORMATION, now);
        sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.REMINDER_1, now);
        sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.REMINDER_2, now);
        sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.REMINDER_3, now);
        sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.ESCALATION_REMINDER, now);
    }
    
	private void sendEmails(final Attestation.AttestationType attestationType, final AttestationMail.MailType mailType, final LocalDate now) {
        final Optional<AttestationRun> run = attestationRunService.getCurrentRun();
        if (run.isEmpty()) {
            return;
        }
        final EmailTemplate template = run.get().isSensitive() ? findSensitiveEmailTemplate(mailType) : findEmailTemplate(attestationType, mailType);
        int daysBefore =  template.getDaysBeforeEvent();
		final List<Attestation> attestations = attestationDao.findAttestationsWhichNeedsMail(attestationType, mailType, now.plusDays(daysBefore));
		attestations.forEach(a -> self.sendEmail(a.getId(), attestationType, mailType));
	}

	private void sendEmailsOrganisation(final Attestation.AttestationType attestationType, final AttestationMail.MailType mailType, final LocalDate now) {
		final Optional<AttestationRun> run = attestationRunService.getCurrentRun();
		if (run.isEmpty()) {
			return;
		}
		
		final EmailTemplate template = run.get().isSensitive() ? findSensitiveEmailTemplate(mailType) : findEmailTemplate(attestationType, mailType);

		int daysBefore =  template.getDaysBeforeEvent();
		LocalDate deadlineDate = now.plusDays(daysBefore);

		final List<Attestation> attestations = run.get().getAttestations().stream()
                .filter(att -> att.getAttestationType() == attestationType)
                .filter(att -> att.getVerifiedAt() == null)
                .filter(a -> a.getMails().stream().noneMatch(mail -> mail.getEmailTemplateType() == template.getTemplateType()))
                .filter(att -> att.getDeadline().isEqual(deadlineDate)).toList();

		boolean escalation = mailType == AttestationMail.MailType.ESCALATION_REMINDER;
		Map<Attestation, List<User>> attestationTargetUsers = null;
		if (escalation) {
			attestationTargetUsers = attestations.stream()
                    .map(att -> Pair.of(att, findTargetUsers(att, true)))
                    .filter(p -> p.getSecond() != null)
                    .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
		} else {
            attestationTargetUsers = attestations.stream()
                    .map(att -> Pair.of(att, findTargetUsers(att, false)))
                    .filter(p -> p.getSecond() != null)
                    .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
		}
		
		Map<User, List<Attestation>> targetUsersAttestation = attestationTargetUsers.entrySet().stream()
				.flatMap(x -> x.getValue().stream().map(v -> Pair.of(x.getKey(), v)))
				.collect(Collectors.toMap(Pair::getSecond, xx -> Collections.singletonList(xx.getFirst()),
                        (a, b) -> Stream.concat(a.stream(), b.stream()).collect(Collectors.toList())));

		targetUsersAttestation.forEach((key, value) -> self.sendEmailsOrganisation(key, value.stream().map(Attestation::getId).toList(), mailType, template));
	}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEmail(final Long attestationId, final Attestation.AttestationType attestationType, AttestationMail.MailType mailType) {
        final Attestation attestation = attestationDao.findById(attestationId)
                .orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, attestation with id %d not found", attestationId));
        final EmailTemplate template = attestation.isSensitive()
                ? findSensitiveEmailTemplate(mailType)
                : findEmailTemplate(attestationType, mailType);
        final User systemResponsible = attestation.getResponsibleUserUuid() != null ? userDao.findByUuidAndDeletedFalse(attestation.getResponsibleUserUuid()).orElse(null) : null;
        if (template.isEnabled()) {
            boolean escalation = mailType == AttestationMail.MailType.ESCALATION_REMINDER;
            final List<User> targetUsers = findTargetUsers(attestation, escalation);

            // In case this is an escalation we need to find the user that was supposed handle the attestation
            User user;
            if (escalation) {
                List<User> users = findTargetUsers(attestation, false);
                user = (users != null && !users.isEmpty()) ? users.getFirst() : null;
            } else {
                user = null;
            }
            if (targetUsers == null) {
                log.warn("No target users was found for attestation with id: " + attestation.getId());
                return;
            }
            
            final List<AttestationMailReceiver> mailReceivers = targetUsers.stream()
                    .flatMap(receiver -> {
                        final String message = resolveMessage(attestation, receiver, user, systemResponsible, template, null);
                        final String title = resolveTitle(attestation, receiver, user, systemResponsible, template);
                        final String email = receiver.getEmail();
                        final Optional<User> cc = findSystemOwner(attestation);
                        log.info("Sending attestation notification ({}, {}, {} to {})", attestation.getItSystemName(),
                                template.getTemplateType().name(), mailType.name(), email);
                        final String ccEmail = cc.map(User::getEmail).orElse(null);
                        emailQueueService.queueEmail(email, title, message, template, null, email.equals(ccEmail) ? null : ccEmail);
                        final List<AttestationMailReceiver> receivers = new ArrayList<>();
                        receivers.add(AttestationMailReceiver.builder()
                                .receiverType(AttestationMailReceiver.ReceiverType.TO)
                                .userUuid(receiver.getUuid())
                                .email(receiver.getEmail())
                                .title(title)
                                .message(message)
                                .email(email)
                                .build());
                        if (ccEmail != null && !ccEmail.equals(email)) {
                            receivers.add(AttestationMailReceiver.builder()
                                    .receiverType(AttestationMailReceiver.ReceiverType.CC)
                                    .userUuid(cc.map(User::getUuid).orElse(null))
                                    .email(ccEmail)
                                    .title(title)
                                    .message(message)
                                    .email(email)
                                    .build());
                        }
                        return receivers.stream();
                    })
                    .toList();
            final AttestationMail mail = attestationMailDao.save(AttestationMail.builder()
                    .attestation(attestation)
                    .emailTemplateType(template.getTemplateType())
                    .emailType(mailType)
                    .sentAt(ZonedDateTime.now())
                    .build());
            mailReceivers.forEach(r -> {
                r.setMail(mail);
                mail.getReceivers().add(r);
            });
            attestation.getMails().add(mail);
        } else {
            log.info(template.getTitle() + " is disabled, notification not sent for attestation: " + attestation.getUuid());
        }
    }

    //New and better :)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEmailsOrganisation(User targetUser, List<Long> attestationIds, AttestationMail.MailType mailType, EmailTemplate template) {
        final User receiver = userDao.findByUuidAndDeletedFalse(targetUser.getUuid()).orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, user with uuid %d not found", targetUser.getUuid()));
        
        //Build a list of attestations
        final List<Attestation> attestations = attestationIds.stream()
                .map(id -> attestationDao.findById(id).orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, attestation with id %d not found", id)))
                .toList();
		if (template.isEnabled()) {
            boolean escalation = mailType == AttestationMail.MailType.ESCALATION_REMINDER;
            
            // In case this is an escalation we need to find the user that was supposed handle the attestation
            User user;
            if (escalation) {
                List<User> users = findTargetUsers(attestations.getFirst(), false);
                user = (users != null && !users.isEmpty()) ? users.getFirst() : null;
            } else {
                user = null;
            }
            
            final String message = resolveMessage(attestations.get(0), receiver, user, null, template, attestations);
            final String title = resolveTitle(attestations.get(0), receiver, user, null, template);
            final String email = receiver.getEmail();
            log.info("Sending attestation notification ({}, {}, {} to {})", attestations.get(0).getItSystemName(),
                    template.getTemplateType().name(), mailType.name(), email);
            emailQueueService.queueEmail(email, title, message, template, null, null);


            for (Attestation attestation : attestations) {
                final AttestationMailReceiver mailReceiver =  AttestationMailReceiver.builder()
                        .receiverType(AttestationMailReceiver.ReceiverType.TO)
                        .userUuid(receiver.getUuid())
                        .email(receiver.getEmail())
                        .title(title)
                        .message(message)
                        .email(email)
                        .build();

                final AttestationMail mail = attestationMailDao.save(AttestationMail.builder()
                        .attestation(attestation)
                        .emailTemplateType(template.getTemplateType())
                        .emailType(mailType)
                        .sentAt(ZonedDateTime.now())
                        .build());
                mailReceiver.setMail(mail);
                mail.getReceivers().add(mailReceiver);
                attestation.getMails().add(mail);
            }
        } else {
            log.info("{} is disabled, notification not sent.", template.getTitle());
        }
    }

    private List<User> findTargetUsers(final Attestation attestation, final boolean escalation) {
        boolean systemEscalation = escalation && attestation.getResponsibleUserUuid() != null;
        User responsibleUser = null;
        if (attestation.getResponsibleUserUuid() != null) {
            responsibleUser = userDao.findByUuidAndDeletedFalse(attestation.getResponsibleUserUuid()).orElse(null);
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

    private String resolveTitle(Attestation attestation, User receiver, final User user, User systemResponsible, EmailTemplate template) {
        String title = template.getTitle();
        // For backwards compatibility we need to replace for {enhed} also
        final List<EmailTemplatePlaceholder> emailTemplatePlaceholders = Stream.concat(template.getTemplateType().getEmailTemplatePlaceholders().stream(), Stream.of(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)).toList();
        for (final EmailTemplatePlaceholder placeholder : emailTemplatePlaceholders) {
            title = StringUtils.replace(title, placeholder.getPlaceholder(), placeholderValue(attestation, receiver, user, systemResponsible, placeholder, null));
        }
        return title;
    }

    private String resolveMessage(final Attestation attestation, final User receiver, final User user, final User systemResponsible, final EmailTemplate template, List<Attestation> attestations) {
        String message = template.getMessage();
        // For backwards compatibility we need to replace for {enhed} also
        final List<EmailTemplatePlaceholder> emailTemplatePlaceholders = Stream.concat(template.getTemplateType().getEmailTemplatePlaceholders().stream(), Stream.of(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)).toList();
        for (final EmailTemplatePlaceholder placeholder : emailTemplatePlaceholders) {
            message = StringUtils.replace(message, placeholder.getPlaceholder(), placeholderValue(attestation, receiver, user, systemResponsible, placeholder, attestations));
        }
        return message;
    }


    private String resolveRequestChangeMailTitle(final String requester, final String user, final String change, final String ou, final EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            title = StringUtils.replace(title, placeholder.getPlaceholder(), placeholderValue(requester, change, user, null, null, null, ou, placeholder));
        }
        return title;
    }

    private String resolveRequestChangeMailMessage(final String requester, final String user, final String change, final EmailTemplate template, List<RoleAssignmentDTO> notApproved, final String ou) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = StringUtils.replace(message, placeholder.getPlaceholder(), placeholderValue(requester, change, user, null, null, notApproved, ou, placeholder));
        }
        return message;
    }


    private String resolveRequestForRoleChangeMailTitle(final String requester, final String role, final String itSystem, final String remark, final EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            title = StringUtils.replace(title, placeholder.getPlaceholder(), placeholderValue(requester, remark, null, role, itSystem, null, null, placeholder));
        }
        return title;
    }

    private String resolveRequestForRoleChangeMailMessage(final String requester, final String role, final String itSystem, final String remark, final EmailTemplate template) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = StringUtils.replace(message, placeholder.getPlaceholder(), placeholderValue(requester, remark, null, role, itSystem, null, null, placeholder));
        }
        return message;
    }

    private String placeholderValue(final Attestation attestation, final User receiver, final User user, final User systemResponsible, final EmailTemplatePlaceholder placeholder, final List<Attestation> attestations) {
        return switch (placeholder) {
            case RECEIVER_PLACEHOLDER -> receiver != null ? receiver.getName() : "ukendt";
            case ORGUNIT_PLACEHOLDER -> attestations != null ? attestations.stream().map(this::findOrgUnitName).collect(Collectors.joining(", ")) : "";
            case USER_PLACEHOLDER -> user != null ? user.getName() : placeholder.getPlaceholder();
            case ITSYSTEM_PLACEHOLDER -> attestation.getItSystemName();
            case SYSTEM_RESPONSIBLE_PLACEHOLDER -> systemResponsible != null ? systemResponsible.getName() : "ukendt";
            case ATTESTATION_DEADLINE -> attestation.getDeadline() != null ? attestation.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "ukendt";
            case ORGUNITS_PLACEHOLDER -> attestations != null ? attestations.stream().map(this::findOrgUnitName).collect(Collectors.joining(", ")) : "";
            default -> {
                log.warn(placeholder.getPlaceholder() + " is not resolved in AttestationEmailNotificationService");
                yield placeholder.getPlaceholder();
            }
        };
    }

    private String placeholderValue(final String requester, final String change, final String user, final String role,
                                    final String itSystem, List<RoleAssignmentDTO> notApproved, final String ou,
                                    final EmailTemplatePlaceholder placeholder) {
        return switch (placeholder) {
            case CHANGE_REQUESTED_PLACEHOLDER -> change != null ? change : placeholder.getPlaceholder();
            case REQUESTER_PLACEHOLDER -> requester;
            case USER_PLACEHOLDER -> user != null ? user : placeholder.getPlaceholder();
            case ROLE_NAME -> role != null ? role : placeholder.getPlaceholder();
            case ITSYSTEM_PLACEHOLDER -> itSystem != null ? itSystem : placeholder.getPlaceholder();
            case LIST_OF_CHANGE_REQUESTS -> notApproved == null ? "" : getChangeList(notApproved);
            case ORGUNIT_PLACEHOLDER -> ou;
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
            stringBuilder.append(dto.getItSystemName());
            stringBuilder.append(": ");
            stringBuilder.append(dto.getRoleType().equals(RoleType.ROLEGROUP) ? "Rollebuketten " : "Jobfunktionsrollen ");
            stringBuilder.append(dto.getRoleName());
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
            return userDao.findByUuidAndDeletedFalse(attestation.getResponsibleUserUuid()).orElseThrow()
                    .getPositions().stream().map(Position::getOrgUnit)
                    .filter(Objects::nonNull)
                    .map(OrgUnit::getName)
                    .collect(Collectors.joining(","));
        }
        return "Ukendt";
    }

    private Optional<User> findSystemOwner(final Attestation attestation) {
        Optional<ItSystem> itSystem = itSystemDao.findById(attestation.getItSystemId());
        return itSystem.map(ItSystem::getSystemOwner);
    }

    private EmailTemplate findEmailTemplate(final Attestation.AttestationType attestationType,  AttestationMail.MailType mailType) {
        return switch (mailType) {
            case INFORMATION -> 
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_NOTIFICATION);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION);
                };
            case REMINDER_1 ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER1);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER1);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER1);
                };
            case REMINDER_2 ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER2);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER2);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER2);
                };
            case REMINDER_3 ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER3);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER3);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER3);
                };
            case ESCALATION_REMINDER ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY);
                };
        };
    }

    private EmailTemplate findSensitiveEmailTemplate(final AttestationMail.MailType mailType) {
        return switch (mailType) {
            case INFORMATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_NOTIFICATION);
            case REMINDER_1 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER1);
            case REMINDER_2 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER2);
            case REMINDER_3 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER3);
            case ESCALATION_REMINDER -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY);
        };
    }

}
