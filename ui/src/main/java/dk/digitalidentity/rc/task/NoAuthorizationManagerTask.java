package dk.digitalidentity.rc.task;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.NotificationEntityType;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;

@EnableScheduling
@Component
public class NoAuthorizationManagerTask {

	@Autowired
	private NotificationService notificationService;
	
	@Autowired
	private SettingsService settingsService;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private NoAuthorizationManagerTask self;
	
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 9 * * ?")
	public void run() {
		if (!configuration.getScheduled().isEnabled() || !settingsService.isRequestApproveEnabled()) {
			return;
		}

		self.flagOrgunits();
	}

	@Transactional
	public void flagOrgunits() {		
		List<Notification> notifications = notificationService.findAllByType(NotificationType.ORGUNIT_WITHOUT_AUTHORIZATION_MANAGER);
		Set<String> uuids = notifications.stream().map(n -> n.getAffectedEntityUuid()).collect(Collectors.toSet()); 

		for (OrgUnit orgUnit : orgUnitService.getAll()) {
			boolean hasSubstitute = (orgUnit.getManager() != null && orgUnit.getManager().getManagerSubstitute() != null);
			boolean hasAuthorizationManager = (hasSubstitute || (orgUnit.getAuthorizationManagers() != null && orgUnit.getAuthorizationManagers().size() > 0));
			boolean alreadyNotified = (uuids.contains(orgUnit.getUuid()));

			if (hasAuthorizationManager && alreadyNotified) {
				Notification notification = notifications.stream().filter(n -> Objects.equals(n.getAffectedEntityUuid(), orgUnit.getUuid())).findFirst().orElse(null);
				if (notification != null) {
					notificationService.delete(notification);
				}
			}
			else if (!hasAuthorizationManager && !alreadyNotified) {
				Notification notification = new Notification();
				notification.setActive(true);
				notification.setCreated(new Date());
				notification.setAffectedEntityName(orgUnit.getName());
				notification.setAffectedEntityUuid(orgUnit.getUuid());
				notification.setAffectedEntityType(NotificationEntityType.OUS);
				notification.setNotificationType(NotificationType.ORGUNIT_WITHOUT_AUTHORIZATION_MANAGER);

				notificationService.save(notification);
			}
		}
	}
}
