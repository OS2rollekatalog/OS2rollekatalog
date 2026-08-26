package dk.digitalidentity.rc.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.ManualAssignmentEffectuationDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.ManualWelcomeEmailTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationStatus;
import dk.digitalidentity.rc.log.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualAssignmentEffectuationService {
	private final ManualAssignmentEffectuationDao manualAssignmentEffectuationDao;
	private final ManualAssignmentEffectuationNotifier manualAssignmentEffectuationNotifier;
	private final EmailQueueService emailQueueService;
	private final EmailTemplateRenderer emailTemplateRenderer;
	private final ManualWelcomeEmailTemplateService manualWelcomeEmailTemplateService;
	private final AuditLogger auditLogger;

	/**
	 * Registers (or cancels) a pending effectuation row for a single (user, userRole) pair.
	 * No-op unless the role's it-system is ItSystemType.MANUAL and has the effectuation flow enabled.
	 * If a pending row for the opposite operation already exists for the exact same (user, userRole),
	 * it is cancelled instead of creating a new row (assign-then-unassign before anyone acted is a no-op).
	 */
	@Transactional
	public void registerPendingEffectuation(User user, UserRole userRole, ManualAssignmentEffectuationOperation operation) {
		ItSystem itSystem = userRole.getItSystem();
		if (itSystem.getSystemType() != ItSystemType.MANUAL || !itSystem.isManualEffectuationEnabled()) {
			return;
		}

		ManualAssignmentEffectuationOperation oppositeOperation = operation == ManualAssignmentEffectuationOperation.ASSIGN
			? ManualAssignmentEffectuationOperation.REMOVE
			: ManualAssignmentEffectuationOperation.ASSIGN;

		Optional<ManualAssignmentEffectuation> pendingOpposite = manualAssignmentEffectuationDao
			.findByUser_UuidAndUserRole_IdAndOperationAndStatus(user.getUuid(), userRole.getId(),
				oppositeOperation, ManualAssignmentEffectuationStatus.PENDING);
		if (pendingOpposite.isPresent()) {
			manualAssignmentEffectuationDao.delete(pendingOpposite.get());
			return;
		}

		Optional<ManualAssignmentEffectuation> alreadyPending = manualAssignmentEffectuationDao
			.findByUser_UuidAndUserRole_IdAndOperationAndStatus(user.getUuid(), userRole.getId(),
				operation, ManualAssignmentEffectuationStatus.PENDING);
		if (alreadyPending.isPresent()) {
			return;
		}

		ManualAssignmentEffectuation effectuation = new ManualAssignmentEffectuation();
		effectuation.setUser(user);
		effectuation.setUserRole(userRole);
		effectuation.setItSystem(itSystem);
		effectuation.setOperation(operation);
		effectuation.setStatus(ManualAssignmentEffectuationStatus.PENDING);
		effectuation.setCreatedAt(LocalDateTime.now());

		manualAssignmentEffectuationDao.save(effectuation);
	}

	@Transactional
	public void markEffectuated(long effectuationId, User performer, String comment) {
		markEffectuated(effectuationId, performer.getUuid(), comment);
	}

	/**
	 * @param performerIdentifier a User's uuid for human completions (via the UI), or a caller-supplied
	 *                             identifier (e.g. an integration/robot name) for API-originated completions -
	 *                             completed_by_user_uuid is a plain string column, not a User foreign key.
	 */
	@Transactional
	public void markEffectuated(long effectuationId, String performerIdentifier, String comment) {
		ManualAssignmentEffectuation effectuation = manualAssignmentEffectuationDao.findById(effectuationId)
			.orElseThrow(() -> new IllegalArgumentException("No such effectuation task: " + effectuationId));

		if (effectuation.getStatus() == ManualAssignmentEffectuationStatus.COMPLETED) {
			return;
		}

		effectuation.setStatus(ManualAssignmentEffectuationStatus.COMPLETED);
		effectuation.setCompletedAt(LocalDateTime.now());
		effectuation.setCompletedByUserUuid(performerIdentifier);
		effectuation.setComment(comment);

		manualAssignmentEffectuationDao.save(effectuation);
		auditLogger.log(effectuation, EventType.MANUAL_EFFECTUATION_COMPLETED, null, performerIdentifier);

		sendEffectuationEmail(effectuation);
	}

	private void sendEffectuationEmail(ManualAssignmentEffectuation effectuation) {
		ItSystem itSystem = effectuation.getItSystem();
		ManualWelcomeEmailTemplate template = manualWelcomeEmailTemplateService.findByItSystem(itSystem, effectuation.getOperation());
		if (!template.isEnabled()) {
			return;
		}

		User user = effectuation.getUser();
		if (!StringUtils.hasLength(user.getEmail())) {
			log.info("Cannot send manual-itsystem effectuation mail to user {} - no email set", user.getUuid());
			return;
		}

		Map<EmailTemplatePlaceholder, String> placeholderData = new EnumMap<>(EmailTemplatePlaceholder.class);
		placeholderData.put(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, user.getName());
		placeholderData.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, itSystem.getName());
		placeholderData.put(EmailTemplatePlaceholder.ROLE_NAME, effectuation.getUserRole() != null ? effectuation.getUserRole().getName() : "");
		placeholderData.put(EmailTemplatePlaceholder.COMMENT_PLACEHOLDER, effectuation.getComment() != null ? effectuation.getComment() : "");

		String title = emailTemplateRenderer.render(template.getTitle(), placeholderData);
		String message = emailTemplateRenderer.render(template.getMessage(), placeholderData);

		emailQueueService.queueEmail(user.getEmail(), title, message, null, null, null);
	}

	public Optional<ManualAssignmentEffectuation> findById(long id) {
		return manualAssignmentEffectuationDao.findById(id);
	}

	public List<ManualAssignmentEffectuation> findPendingForItSystems(List<Long> itSystemIds) {
		return manualAssignmentEffectuationDao.findByStatusAndItSystem_IdIn(ManualAssignmentEffectuationStatus.PENDING, itSystemIds);
	}

	public List<ManualAssignmentEffectuation> findAllPending() {
		return manualAssignmentEffectuationDao.findByStatus(ManualAssignmentEffectuationStatus.PENDING);
	}

	public long countPendingForItSystems(List<Long> itSystemIds) {
		return manualAssignmentEffectuationDao.countByStatusAndItSystem_IdIn(ManualAssignmentEffectuationStatus.PENDING, itSystemIds);
	}

	public long countAllPending() {
		return manualAssignmentEffectuationDao.countByStatus(ManualAssignmentEffectuationStatus.PENDING);
	}

	/**
	 * Sends one digest email per system owner per IT-system, summarizing every pending
	 * effectuation not yet covered by a previous digest - instead of one email per task, which
	 * would flood system owners when many users are affected by a single OU/rolegroup change.
	 */
	@Transactional
	public void notifyPendingEffectuationsDigest() {
		List<ManualAssignmentEffectuation> notYetNotified = manualAssignmentEffectuationDao
			.findByStatusAndEmailSent(ManualAssignmentEffectuationStatus.PENDING, false);

		if (notYetNotified.isEmpty()) {
			return;
		}

		manualAssignmentEffectuationNotifier.notifySystemOwnersOfPendingEffectuationDigest(notYetNotified);

		notYetNotified.forEach(e -> e.setEmailSent(true));
		manualAssignmentEffectuationDao.saveAll(notYetNotified);
	}

	/**
	 * Deletes completed (COMPLETED) tasks older than the given retention period. Pending
	 * (PENDING) tasks are never touched regardless of age - only completed history is pruned.
	 * The completion itself remains discoverable afterwards via the audit log
	 * (EventType.MANUAL_EFFECTUATION_COMPLETED), written at completion time in markEffectuated.
	 */
	@Transactional
	public long deleteEffectuatedOlderThan(LocalDateTime completedBefore) {
		return manualAssignmentEffectuationDao.deleteByStatusAndCompletedAtBefore(ManualAssignmentEffectuationStatus.COMPLETED, completedBefore);
	}
}
