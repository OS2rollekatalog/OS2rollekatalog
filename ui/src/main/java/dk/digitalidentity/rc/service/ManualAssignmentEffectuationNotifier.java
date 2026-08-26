package dk.digitalidentity.rc.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemSystemOwner;
import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.saml.config.SamlConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends one digest email per system owner per IT-system, summarizing however many pending
 * effectuation tasks have accumulated since the last digest - rather than one email per task,
 * which would flood system owners when e.g. a role is assigned to a large OU at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualAssignmentEffectuationNotifier {
	private final EmailTemplateService emailTemplateService;
	private final EmailTemplateRenderer emailTemplateRenderer;
	private final EmailQueueService emailQueueService;
	private final SamlConfiguration samlConfiguration;

	public void notifySystemOwnersOfPendingEffectuations(ItSystem itSystem, List<ManualAssignmentEffectuation> pending) {
		if (pending.isEmpty()) {
			return;
		}

		if (itSystem.getSystemOwners() == null || itSystem.getSystemOwners().isEmpty()) {
			log.info("No system owners registered for it-system {}, cannot notify about {} pending effectuation(s)", itSystem.getId(), pending.size());
			return;
		}

		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING);
		if (!template.isEnabled()) {
			log.info("Email template with type {} is disabled. Email was not sent.", template.getTemplateType());
			return;
		}

		String ids = pending.stream().map(e -> Long.toString(e.getId())).collect(Collectors.joining(","));
		String link = samlConfiguration.getSp().getBaseUrl() + "/ui/manualeffectuation?ids=" + ids;
		String count = Integer.toString(pending.size());

		for (ItSystemSystemOwner systemOwner : itSystem.getSystemOwners()) {
			User owner = systemOwner.getUser();
			if (owner == null || !StringUtils.hasLength(owner.getEmail())) {
				continue;
			}

			Map<EmailTemplatePlaceholder, String> placeholderData = new EnumMap<>(EmailTemplatePlaceholder.class);
			placeholderData.put(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, owner.getName());
			placeholderData.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, itSystem.getName());
			placeholderData.put(EmailTemplatePlaceholder.COUNT_PLACEHOLDER, count);
			placeholderData.put(EmailTemplatePlaceholder.TASK_LINK_PLACEHOLDER, link);

			String title = emailTemplateRenderer.renderTitle(template, placeholderData);
			String message = emailTemplateRenderer.render(template.getMessage(), placeholderData);

			emailQueueService.queueEmail(owner.getEmail(), title, message, template, null, null);
		}
	}

	/**
	 * Groups pending, not-yet-emailed effectuations by IT-system and sends one digest per group.
	 * Grouped by id, not by the ItSystem entity itself: ItSystem uses Lombok @Data, and its
	 * systemOwners/attestationResponsibles collections hold entities with a back-reference to their
	 * owning ItSystem (also @Data) - hashing/equating the entity directly recurses infinitely.
	 */
	public void notifySystemOwnersOfPendingEffectuationDigest(List<ManualAssignmentEffectuation> pending) {
		Map<Long, List<ManualAssignmentEffectuation>> byItSystemId = pending.stream()
			.collect(Collectors.groupingBy(e -> e.getItSystem().getId()));

		for (List<ManualAssignmentEffectuation> group : byItSystemId.values()) {
			ItSystem itSystem = group.get(0).getItSystem();
			notifySystemOwnersOfPendingEffectuations(itSystem, group);
		}
	}
}
