package dk.digitalidentity.rc.event;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.enums.NotificationEntityType;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.model.OrgUnitWithTitlesDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class NewTitlesInOrgUnitsEventHandler {

	private final SettingsService settingsService;
	private final NotificationService notificationService;
	private final RoleCatalogueConfiguration configuration;

	@EventListener(NewTitlesInOrgUnitsEvent.class)
	public void handleNewTitlesInOrgUnitsEvent(NewTitlesInOrgUnitsEvent event) {
		log.debug("NewTitlesInOrgUnitsEvent received, {} org units with new titles", event.getOrgUnitsWithNewTitles().size());
		if (!configuration.getTitles().isEnabled()) {
			log.debug("Titles are disabled, skipping notifications");
			return;
		}

		for (OrgUnitWithTitlesDTO dto : event.getOrgUnitsWithNewTitles()) {
			String ouName = dto.getOrgUnit().getName();
			String ouUuid = dto.getOrgUnit().getUuid();

			boolean ouHasTitleScopedAssignments =
				dto.getOrgUnit().getUserRoleAssignments().stream().anyMatch(a -> !a.getTitles().isEmpty()) ||
				dto.getOrgUnit().getRoleGroupAssignments().stream().anyMatch(a -> !a.getTitles().isEmpty());

			// NEW_TITLE_IN_ORG_UNIT fires for all new titles on OUs that have title-scoped assignments
			if (ouHasTitleScopedAssignments && settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)) {
				StringBuilder message = new StringBuilder("Enheden '" + ouName + "' har fået tilføjet følgende nye stillinger:\n");
				dto.getNewTitles().forEach(t -> message.append(t.getName()).append("\n"));
				notificationService.save(createNotification(message.toString(), NotificationType.NEW_TITLE_IN_ORG_UNIT, ouName, NotificationEntityType.OUS, ouUuid));
			}

			// RETURNING_TITLE_IN_ORG_UNIT fires only for titles that have no prior assignments on this OU
			List<Title> titlesWithNoPriorAssignments = new ArrayList<>();
			for (Title newTitle : dto.getNewTitles()) {
				boolean hasPriorUserRoleAssignment = dto.getOrgUnit().getUserRoleAssignments().stream()
					.anyMatch(a -> a.getTitles().stream().anyMatch(t -> t.getUuid().equals(newTitle.getUuid())));
				boolean hasPriorRoleGroupAssignment = dto.getOrgUnit().getRoleGroupAssignments().stream()
					.anyMatch(a -> a.getTitles().stream().anyMatch(t -> t.getUuid().equals(newTitle.getUuid())));
				if (!hasPriorUserRoleAssignment && !hasPriorRoleGroupAssignment) {
					titlesWithNoPriorAssignments.add(newTitle);
				}
			}

			if (!titlesWithNoPriorAssignments.isEmpty() && settingsService.isNotificationTypeEnabled(NotificationType.RETURNING_TITLE_IN_ORG_UNIT)) {
				StringBuilder message = new StringBuilder("Enheden '" + ouName + "' har fået tilføjet følgende nye stillinger, hvor der ikke tidligere er tildelt rettigheder:\n");
				titlesWithNoPriorAssignments.forEach(t -> message.append(t.getName()).append("\n"));
				notificationService.save(createNotification(message.toString(), NotificationType.RETURNING_TITLE_IN_ORG_UNIT, ouName, NotificationEntityType.OUS, ouUuid));
			}
		}
	}

	private static Notification createNotification(String message, NotificationType notificationType, String affectedEntityName, NotificationEntityType entityType, String affectedEntityUuid) {
		Notification notification = new Notification();
		notification.setActive(true);
		notification.setCreated(new Date());
		notification.setMessage(message);
		notification.setNotificationType(notificationType);
		notification.setAffectedEntityName(affectedEntityName);
		notification.setAffectedEntityType(entityType);
		notification.setAffectedEntityUuid(affectedEntityUuid);
		return notification;
	}
}
