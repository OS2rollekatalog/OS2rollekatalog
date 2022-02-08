package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.NotificationDao;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;

@Service
public class NotificationService {

	@Autowired
	private NotificationDao notificationDao;
	
	@Autowired
	private SettingsService settingsService;
	
	public long countActive() {
		return notificationDao.countByActiveTrue();
	}
	
	public List<Notification> findAll() {
		return notificationDao.findAll();
	}

	public Notification findById(long id) {
		return notificationDao.findById(id);
	}

	public Notification save(Notification notification) {
		// Only save if enabled in settings
		if (settingsService.isNotificationTypeEnabled(notification.getNotificationType())) {
			return notificationDao.save(notification);
		}

		return null;
	}
	
	public List<Notification> findAllByType(NotificationType type) {
		return notificationDao.findAllByNotificationType(type);
	}

	public void delete(Notification notification) {
		notificationDao.delete(notification);
	}
	
	@Transactional
	public void deleteAllByNotificationType(NotificationType type) {
		notificationDao.deleteByNotificationType(type);
	}
}
