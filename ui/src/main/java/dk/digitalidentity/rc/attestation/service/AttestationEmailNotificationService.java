package dk.digitalidentity.rc.attestation.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mysema.commons.lang.Pair;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationMailDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.exception.AttestationEmailNotificationException;
import dk.digitalidentity.rc.attestation.model.AttestationMailRecipient;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMailReceiver;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.AttestationEscalationRecipients;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
public class AttestationEmailNotificationService {

    /** The last mail type has no successor to close its window, so it is caught up for this many days instead. */
    private static final int LAST_WINDOW_CATCH_UP_DAYS = 7;

    @Autowired
    private AttestationDao attestationDao;

    @Autowired
    private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

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
	@Autowired
	private ItSystemService itSystemService;
	@Autowired
	private OrgUnitService orgUnitService;
	@Autowired
	private ManagerDelegateService managerDelegateService;


    public void sendRequestForAdRemoval(final String requester, final String user, final String responsibleOu) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_REMOVAL);
        if (!template.isEnabled()) {
            log.info("{} is disabled, request for deletion notification not sent (leder={}, user={})", template.getTitle(), requester, user);
            return;
        }
        final String message = resolveRequestChangeMailMessage(requester, user, null, template, null, responsibleOu);
        final String title = resolveRequestChangeMailTitle(requester, user, null, responsibleOu, template);
        final String email = settingsService.getAttestationChangeEmail();
        log.info("Sending request for deletion notification (leder=" + requester + ", user=" + user + ")");
        emailQueueService.queueEmail(email, title, message, template, null, null);
    }


    public void sendRequestForChangeMail(final String requester, final String user, final String change, List<RoleAssignmentDTO> notApproved) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE);
        if (!template.isEnabled()) {
            log.info("{} is disabled, request for change notification not sent (leder={}, change={})", template.getTitle(), requester, change);
            return;
        }
        log.info("Sending request for change notification (leder={}, change={})", requester, change);
        final String message = resolveRequestChangeMailMessage(requester, user, change, template, notApproved, null);
        final String title = resolveRequestChangeMailTitle(requester, user, null, null, template);
        final String email = settingsService.getAttestationChangeEmail();
        emailQueueService.queueEmail(email, title, message, template, null, null);
    }

    public void sendRequestForRoleChange(final String requester, final String userRole, final String itSystem, final String remark) {
        final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_ROLE_CHANGE);
        if (!template.isEnabled()) {
            log.info("{} is disabled, request for role change notification not sent (leder={}, it-system={}, userRole={})", template.getTitle(), requester, itSystem, userRole);
            return;
        }
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

        sendEmailsOrganisation(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, AttestationMail.MailType.INFORMATION, now);
        sendEmailsOrganisation(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, AttestationMail.MailType.REMINDER_1, now);
        sendEmailsOrganisation(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, AttestationMail.MailType.REMINDER_2, now);
        sendEmailsOrganisation(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, AttestationMail.MailType.REMINDER_3, now);
        sendEmailsOrganisation(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, AttestationMail.MailType.ESCALATION_REMINDER, now);
    }

	private void sendEmails(final Attestation.AttestationType attestationType, final AttestationMail.MailType mailType, final LocalDate now) {
        final Optional<AttestationRun> run = attestationRunService.getCurrentRun();
        if (run.isEmpty()) {
            return;
        }
        final EmailTemplate template = run.get().isSensitive() ? findSensitiveEmailTemplate(attestationType, mailType) : findEmailTemplate(attestationType, mailType);
        int daysBefore =  template.getDaysBeforeEvent();
		final List<Attestation> attestations = attestationDao.findAttestationsWhichNeedsMail(attestationType, mailType, run.get(),
				now.plusDays(daysBefore), now.plusDays(windowClosesDaysBefore(attestationType, run.get(), daysBefore)))
				.stream()
				.filter(att -> existedOnSendDate(att, daysBefore))
				.toList();
		final AtomicInteger failed = new AtomicInteger();
		attestations.forEach(a -> {
			try {
				self.sendEmail(a.getId(), attestationType, mailType);
			} catch (final Exception e) {
				failed.incrementAndGet();
				log.error("Failed to send {} {} for attestation {}, continuing with the rest", attestationType, mailType, a.getId(), e);
			}
		});
		if (failed.get() > 0) {
			log.error("{} of {} {} {} notifications failed", failed.get(), attestations.size(), attestationType, mailType);
		}
	}

	void sendEmailsOrganisation(final Attestation.AttestationType attestationType, final AttestationMail.MailType mailType, final LocalDate now) {
		final Optional<AttestationRun> run = attestationRunService.getCurrentRun();
		if (run.isEmpty()) {
			return;
		}

		final EmailTemplate template = run.get().isSensitive() ? findSensitiveEmailTemplate(attestationType, mailType) : findEmailTemplate(attestationType, mailType);

		int daysBefore =  template.getDaysBeforeEvent();
		LocalDate windowOpened = now.plusDays(daysBefore);
		LocalDate windowClosed = now.plusDays(windowClosesDaysBefore(attestationType, run.get(), daysBefore));

		final List<Attestation> attestations = run.get().getAttestations().stream()
                .filter(att -> att.getAttestationType() == attestationType)
                .filter(att -> att.getVerifiedAt() == null)
                .filter(att -> !att.getDeadline().isAfter(windowOpened) && att.getDeadline().isAfter(windowClosed))
                .filter(att -> existedOnSendDate(att, daysBefore)).toList();

		final boolean escalation = mailType == AttestationMail.MailType.ESCALATION_REMINDER;
		final Map<Pair<AttestationMailRecipient, String>, List<Attestation>> attestationsByRecipient = attestations.stream()
				.flatMap(attestation -> {
					final List<AttestationMailRecipient> recipients = findRecipients(attestation, mailType).stream()
							.filter(recipient -> !alreadyMailed(attestation, recipient, template.getTemplateType()))
							.toList();
					if (recipients.isEmpty()) {
						return Stream.empty();
					}
					// {bruger} names a single person, so escalations are also grouped per overdue attester
					final String overdueAttesterUuid = escalation ? findOverdueAttester(attestation).map(User::getUuid).orElse("") : "";
					return recipients.stream().map(recipient -> Pair.of(Pair.of(recipient, overdueAttesterUuid), attestation));
				})
				.collect(Collectors.toMap(
						Pair::getFirst,
						receiverAndAttestation -> Collections.singletonList(receiverAndAttestation.getSecond()),
						(attestationsA, attestationsB) -> Stream.concat(attestationsA.stream(), attestationsB.stream()).collect(Collectors.toList())));

		final AtomicInteger failed = new AtomicInteger();
		attestationsByRecipient.forEach((key, value) -> {
			try {
				self.sendEmailsOrganisation(key.getFirst(), value.stream().map(Attestation::getId).toList(), mailType, template);
			} catch (final Exception e) {
				failed.incrementAndGet();
				log.error("Failed to send {} to {}, continuing with the rest", template.getTemplateType(),
						describeFailedRecipient(key.getFirst()), e);
			}
		});
		if (failed.get() > 0) {
			log.error("{} of {} receivers failed for {}", failed.get(), attestationsByRecipient.size(), template.getTemplateType());
		}
	}

	/** A send window closes when the next mail type's opens, so a missed mail is caught up without overlapping the next. */
	private int windowClosesDaysBefore(final Attestation.AttestationType attestationType, final AttestationRun run, final int daysBefore) {
		return Arrays.stream(AttestationMail.MailType.values())
				.map(type -> run.isSensitive() ? findSensitiveEmailTemplate(attestationType, type) : findEmailTemplate(attestationType, type))
				.filter(Objects::nonNull)
				.map(EmailTemplate::getDaysBeforeEvent)
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.filter(days -> days < daysBefore)
				.max()
				.orElse(daysBefore - LAST_WINDOW_CATCH_UP_DAYS);
	}

	/** Catch-up is for mails that failed, not for attestations added to the run after the mail was due. */
	private static boolean existedOnSendDate(final Attestation attestation, final int daysBefore) {
		return attestation.getCreatedAt() == null || !attestation.getCreatedAt().isAfter(attestation.getDeadline().minusDays(daysBefore));
	}

	/** Per receiver, since each receiver is sent in its own transaction and one can fail while another on the same attestation succeeds. */
	private static boolean alreadyMailed(final Attestation attestation, final AttestationMailRecipient recipient, final EmailTemplateType templateType) {
		return attestation.getMails().stream()
				.filter(mail -> mail.getEmailTemplateType() == templateType)
				.flatMap(mail -> mail.getReceivers().stream())
				.anyMatch(recipient::matches);
	}

	private List<AttestationMailRecipient> findRecipients(final Attestation attestation, final AttestationMail.MailType mailType) {
		if (mailType == AttestationMail.MailType.ESCALATION_REMINDER) {
			return findEscalationRecipients(attestation);
		}
		return findAttesters(attestation).stream().map(AttestationMailRecipient::forUser).toList();
	}

	private List<AttestationMailRecipient> findEscalationRecipients(final Attestation attestation) {
		final AttestationEscalationRecipients setting = settingsService.getAttestationEscalationRecipients();
		final List<User> managers = setting.includesManager() ? findEscalationManagers(attestation) : List.of();
		final List<String> fixedAddresses = setting.includesFixedAddresses() ? settingsService.getAttestationEscalationEmails() : List.of();
		return Stream.concat(
				managers.stream().map(AttestationMailRecipient::forUser),
				fixedAddresses.stream().map(AttestationMailRecipient::forAddress))
				.toList();
	}

	private static String describeFailedRecipient(final AttestationMailRecipient recipient) {
		return recipient.isUser()
				? "%s (%s, %s)".formatted(recipient.name(), recipient.userUuid(), recipient.email())
				: "%s (fixed address from attestation settings)".formatted(recipient.email());
	}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEmail(final Long attestationId, final Attestation.AttestationType attestationType, AttestationMail.MailType mailType) {
        final Attestation attestation = attestationDao.findById(attestationId)
                .orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, attestation with id %d not found", attestationId));
        final EmailTemplate template = attestation.isSensitive()
                ? findSensitiveEmailTemplate(attestationType, mailType)
                : findEmailTemplate(attestationType, mailType);
        final User systemResponsible = resolveCollectionUsers(attestation).stream().findFirst().orElse(null);
        if (template.isEnabled()) {
            final List<AttestationMailRecipient> recipients = findRecipients(attestation, mailType);
            if (recipients.isEmpty()) {
                log.warn("No recipients was found for attestation with id: " + attestation.getId());
                return;
            }
            final User overdueAttester = mailType == AttestationMail.MailType.ESCALATION_REMINDER ? findOverdueAttester(attestation).orElse(null) : null;

            final List<User> systemOwners = findSystemOwner(attestation);
            final List<AttestationMailReceiver> mailReceivers = recipients.stream()
                    .flatMap(receiver -> {
                        final String message = resolveMessage(attestation, receiver.name(), overdueAttester, systemResponsible, template, null);
                        final String title = resolveTitle(attestation, receiver.name(), overdueAttester, systemResponsible, template);
                        final String email = receiver.email();
                        final List<User> ccOwners = systemOwners.stream()
                                .filter(o -> !Objects.equals(o.getEmail(), email))
                                .toList();
                        final String ccEmails = ccOwners.stream().map(User::getEmail).collect(Collectors.joining(","));
                        log.info("Sending attestation notification ({}, {}, {} to {})", attestation.getItSystemName(),
                                template.getTemplateType().name(), mailType.name(), email);
                        emailQueueService.queueEmail(email, title, message, template, null, ccEmails.isEmpty() ? null : ccEmails);
                        final List<AttestationMailReceiver> receivers = new ArrayList<>();
                        receivers.add(AttestationMailReceiver.builder()
                                .receiverType(AttestationMailReceiver.ReceiverType.TO)
                                .userUuid(receiver.userUuid())
                                .email(email)
                                .title(title)
                                .message(message)
                                .build());
                        ccOwners.forEach(ccOwner -> receivers.add(AttestationMailReceiver.builder()
                                .receiverType(AttestationMailReceiver.ReceiverType.CC)
                                .userUuid(ccOwner.getUuid())
                                .email(ccOwner.getEmail())
                                .title(title)
                                .message(message)
                                .build()));
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEmailsOrganisation(AttestationMailRecipient targetRecipient, List<Long> attestationIds, AttestationMail.MailType mailType, EmailTemplate template) {
        final AttestationMailRecipient receiver = targetRecipient.isUser()
                ? userDao.findByUuidAndDeletedFalse(targetRecipient.userUuid()).map(AttestationMailRecipient::forUser).orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, user with uuid %s not found", targetRecipient.userUuid()))
                : targetRecipient;

        //Build a list of attestations
        final List<Attestation> attestations = attestationIds.stream()
                .map(id -> attestationDao.findById(id).orElseThrow(() -> new AttestationEmailNotificationException("Failed to send notification, attestation with id %d not found", id)))
                .toList();
		if (template.isEnabled()) {
            final User overdueAttester = mailType == AttestationMail.MailType.ESCALATION_REMINDER ? findOverdueAttester(attestations.getFirst()).orElse(null) : null;
            final String message = resolveMessage(attestations.getFirst(), receiver.name(), overdueAttester, null, template, attestations);
            final String title = resolveTitle(attestations.getFirst(), receiver.name(), overdueAttester, null, template);
            final String email = receiver.email();
            log.info("Sending attestation notification ({}, {}, {} to {})", attestations.getFirst().getItSystemName(),
                    template.getTemplateType().name(), mailType.name(), email);
            emailQueueService.queueEmail(email, title, message, template, null, null);


            for (Attestation attestation : attestations) {
                final AttestationMailReceiver mailReceiver =  AttestationMailReceiver.builder()
                        .receiverType(AttestationMailReceiver.ReceiverType.TO)
                        .userUuid(receiver.userUuid())
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

    /** Whoever must attest: the unit's manager and substitutes, the personal approvers, or the system responsibles. */
    List<User> findAttesters(final Attestation attestation) {
        final List<User> collectionUsers = resolveCollectionUsers(attestation);
        return findResponsibleUser(attestation, collectionUsers)
                .map(responsibleUser -> switch (attestation.getAttestationType()) {
                    case ORGANISATION_ATTESTATION -> managerAndSubstitutes(attestation, responsibleUser);
                    case MANAGER_DELEGATED_ATTESTATION -> managerDelegateService.getByManager(responsibleUser).stream()
                            .map(ManagerDelegate::getDelegate)
                            .toList();
                    default -> collectionUsers;
                })
                .orElse(List.of());
    }

    /** The managers an overdue attestation escalates to. */
    private List<User> findEscalationManagers(final Attestation attestation) {
        final List<User> collectionUsers = resolveCollectionUsers(attestation);
        if (!collectionUsers.isEmpty()) {
            return getManagerOrSubstitute(collectionUsers.stream()
                    .flatMap(u -> u.getPositions().stream())
                    .map(Position::getOrgUnit)
                    .map(OrgUnit::getManager)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        return findResponsibleUser(attestation, collectionUsers)
                .map(responsibleUser -> switch (attestation.getAttestationType()) {
                    case ORGANISATION_ATTESTATION -> getManagerOrSubstitute(responsibleUser.getPositions().stream()
                            .map(Position::getOrgUnit)
                            .filter(ou -> ou.getManager() != null && ou.getManager().getUuid().equals(responsibleUser.getUuid()))
                            .flatMap(ou -> findManagersManager(responsibleUser, ou, 0).stream())
                            .toList());
                    case MANAGER_DELEGATED_ATTESTATION -> managerAndSubstitutes(attestation, responsibleUser);
                    default -> List.<User>of();
                })
                .orElse(List.of());
    }

    private Optional<User> findOverdueAttester(final Attestation attestation) {
        return findAttesters(attestation).stream().findFirst();
    }

    private Optional<User> findResponsibleUser(final Attestation attestation, final List<User> collectionUsers) {
        if (!collectionUsers.isEmpty()) {
            return Optional.of(collectionUsers.getFirst());
        }
        return Optional.ofNullable(attestation.getResponsibleOuUuid())
                .flatMap(orgUnitDao::findById)
                .map(OrgUnit::getManager);
    }

    private List<User> managerAndSubstitutes(final Attestation attestation, final User manager) {
        final List<User> substitutes = orgUnitDao.findById(attestation.getResponsibleOuUuid())
                .stream()
                .map(OrgUnit::getManager)
                .filter(Objects::nonNull)
                .flatMap(ouManager -> ouManager.getManagerSubstitutes().stream())
                .filter(s -> s.getOrgUnit().getUuid().equals(attestation.getResponsibleOuUuid()))
                .map(ManagerSubstitute::getSubstitute)
                .toList();
        if (settingsService.isDontSendMailToManagerEnabled() && !substitutes.isEmpty()) {
            return substitutes;
        }
        return Stream.concat(Stream.of(manager), substitutes.stream()).toList();
    }

    private List<User> getManagerOrSubstitute(List<User> targetUsers) {
        if (!settingsService.isDontSendMailToManagerEnabled()) {
            return targetUsers;
        } else {
            List<User> result = new ArrayList<>();
            for (User user : targetUsers) {
                // Find substitutes(stedfortrædere)
                List<User> substitutes = orgUnitDao.findById(user.getUuid())
                        .stream()
                        .map(OrgUnit::getManager)
                        .filter(Objects::nonNull)
                        .flatMap(manager -> manager.getManagerSubstitutes().stream())
                        .filter(s -> s.getOrgUnit().getUuid().equals(user.getUuid()))
                        .map(ManagerSubstitute::getSubstitute)
                        .toList();
                if (!substitutes.isEmpty()) {
                    result.addAll(substitutes);
                } else {
                    result.add(user);
                }
            }

            return result;
        }
    }

    private List<User> findManagersManager(final User manager, final OrgUnit ou, int depth) {
        if (depth > 10) {
            // In case the OU hierarchy have circular structure, bail out here
            return Collections.emptyList();
        }
        if (ou == null) {
            return Collections.emptyList();
        }
        if (ou.getManager() != null && !ou.getManager().getUuid().equals(manager.getUuid())) {
            return List.of(ou.getManager());
        }
        return findManagersManager(manager, ou.getParent(), depth + 1);
    }

    private String resolveTitle(Attestation attestation, String receiverName, final User user, User systemResponsible, EmailTemplate template) {
        String title = template.getTitle();
        // For backwards compatibility we need to replace for {enhed} also
        final List<EmailTemplatePlaceholder> emailTemplatePlaceholders = Stream.concat(template.getTemplateType().getEmailTemplatePlaceholders().stream(), Stream.of(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)).toList();
        for (final EmailTemplatePlaceholder placeholder : emailTemplatePlaceholders) {
            title = title.replace(placeholder.getPlaceholder(), placeholderValue(attestation, receiverName, user, systemResponsible, placeholder, null));
        }
        return title;
    }

    private String resolveMessage(final Attestation attestation, final String receiverName, final User user, final User systemResponsible, final EmailTemplate template, List<Attestation> attestations) {
        String message = template.getMessage();
        // For backwards compatibility we need to replace for {enhed} also
        final List<EmailTemplatePlaceholder> emailTemplatePlaceholders = Stream.concat(template.getTemplateType().getEmailTemplatePlaceholders().stream(), Stream.of(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)).toList();
        for (final EmailTemplatePlaceholder placeholder : emailTemplatePlaceholders) {
            message = message.replace(placeholder.getPlaceholder(), placeholderValue(attestation, receiverName, user, systemResponsible, placeholder, attestations));
        }
        return message;
    }


    private String resolveRequestChangeMailTitle(final String requester, final String user, final String change, final String ou, final EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
        	title = title.replace(placeholder.getPlaceholder(), placeholderValue(requester, change, user, null, null, null, ou, placeholder));
        }
        return title;
    }

    private String resolveRequestChangeMailMessage(final String requester, final String user, final String change, final EmailTemplate template, List<RoleAssignmentDTO> notApproved, final String ou) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = message.replace(placeholder.getPlaceholder(), placeholderValue(requester, change, user, null, null, notApproved, ou, placeholder));
        }
        return message;
    }


    private String resolveRequestForRoleChangeMailTitle(final String requester, final String role, final String itSystem, final String remark, final EmailTemplate template) {
        String title = template.getTitle();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            title = title.replace(placeholder.getPlaceholder(), placeholderValue(requester, remark, null, role, itSystem, null, null, placeholder));
        }
        return title;
    }

    private String resolveRequestForRoleChangeMailMessage(final String requester, final String role, final String itSystem, final String remark, final EmailTemplate template) {
        String message = template.getMessage();
        for (final EmailTemplatePlaceholder placeholder : template.getTemplateType().getEmailTemplatePlaceholders()) {
            message = message.replace(placeholder.getPlaceholder(), placeholderValue(requester, remark, null, role, itSystem, null, null, placeholder));
        }
        return message;
    }

    private String placeholderValue(final Attestation attestation, final String receiverName, final User user, final User systemResponsible, final EmailTemplatePlaceholder placeholder, final List<Attestation> attestations) {
        return switch (placeholder) {
            case RECEIVER_PLACEHOLDER -> receiverName != null ? receiverName : "ukendt";
            case ORGUNIT_PLACEHOLDER -> attestations != null ? attestations.stream().map(this::findOrgUnitName).collect(Collectors.joining(", ")) : "";
            case USER_PLACEHOLDER -> user != null ? user.getName() : placeholder.getPlaceholder();
            case ITSYSTEM_PLACEHOLDER -> attestation.getItSystemName();
            case SYSTEM_RESPONSIBLE_PLACEHOLDER -> systemResponsible != null ? systemResponsible.getName() : "ukendt";
            case ATTESTATION_DEADLINE -> attestation.getDeadline() != null ? attestation.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "ukendt";
            case ORGUNITS_PLACEHOLDER -> attestations != null ? attestations.stream().map(this::findOrgUnitName).collect(Collectors.joining(", ")) : "";
            case MANAGERDELEGATE_PLACEHOLDER -> findDelegatedForName(attestation);
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
        if (attestation.getResponsibleCollectionId() != null) {
            return resolveCollectionUsers(attestation).stream()
                    .flatMap(u -> u.getPositions().stream())
                    .map(Position::getOrgUnit)
                    .filter(Objects::nonNull)
                    .map(OrgUnit::getName)
                    .distinct()
                    .collect(Collectors.joining(", "));
        }
        return "Ukendt";
    }

    private List<User> resolveCollectionUsers(final Attestation attestation) {
        if (attestation.getResponsibleCollectionId() == null) {
            return Collections.emptyList();
        }
        return attestationResponsibleCollectionDao.findById(attestation.getResponsibleCollectionId())
                .map(c -> userDao.findByUuidInAndDeletedFalse(new java.util.HashSet<>(c.getUsersUuid())))
                .orElse(Collections.emptyList());
    }

    private List<User> findSystemOwner(final Attestation attestation) {
        if (attestation.getItSystemId() == null) {
            return Collections.emptyList();
        }
        return itSystemDao.findById(attestation.getItSystemId())
                .map(itSystem -> itSystemService.getSystemOwners(itSystem))
                .orElse(Collections.emptyList());
    }

    private String findDelegatedForName(final Attestation attestation) {
        if (attestation.getResponsibleOuUuid() == null) {
            return "Ukendt";
        }

        var orgUnit = orgUnitDao.findById(attestation.getResponsibleOuUuid())
                .orElse(null);
        if (orgUnit == null) {
            return "Ukendt";
        }
        var manager = orgUnitService.getManager(orgUnit);
        return managerDelegateService.getByManager(manager).stream()
                .map(md -> md.getManager().getName())
                .findFirst()
                .orElse("Ukendt");
    }

    private EmailTemplate findEmailTemplate(final Attestation.AttestationType attestationType,  AttestationMail.MailType mailType) {
        return switch (mailType) {
            case INFORMATION ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_NOTIFICATION);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION);
                    case MANAGER_DELEGATED_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_MANAGERDELEGATE_NOTIFICATION);
                };
            case REMINDER_1 ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER1);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER1);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER1);
                    case MANAGER_DELEGATED_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_MANAGERDELEGATE_REMINDER1);
                };
            case REMINDER_2 ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER2);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER2);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER2);
                    case MANAGER_DELEGATED_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_MANAGERDELEGATE_REMINDER2);
                };
            case REMINDER_3 ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER3);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER3);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER3);
                    case MANAGER_DELEGATED_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_MANAGERDELEGATE_REMINDER3);
                };
            case ESCALATION_REMINDER ->
                switch (attestationType) {
                    case ORGANISATION_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY);
                    case IT_SYSTEM_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY);
                    case IT_SYSTEM_ROLES_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY);
                    case MANAGER_DELEGATED_ATTESTATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_MANAGERDELEGATE_REMINDER_THIRDPARTY);
                };
        };
    }

    private EmailTemplate findSensitiveEmailTemplate(final Attestation.AttestationType attestationType, final AttestationMail.MailType mailType) {
        if (attestationType == Attestation.AttestationType.IT_SYSTEM_ATTESTATION) {
            return switch (mailType) {
                case INFORMATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_NOTIFICATION);
                case REMINDER_1 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER1);
                case REMINDER_2 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER2);
                case REMINDER_3 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER3);
                case ESCALATION_REMINDER -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY);
            };
        }
        return switch (mailType) {
            case INFORMATION -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_NOTIFICATION);
            case REMINDER_1 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER1);
            case REMINDER_2 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER2);
            case REMINDER_3 -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER3);
            case ESCALATION_REMINDER -> emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY);
        };
    }

}
