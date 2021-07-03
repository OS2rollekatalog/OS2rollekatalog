package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.NotificationDao;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;

@Service
public class NotificationService {

	@Autowired
	private NotificationDao notificationDao;
	
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
		return notificationDao.save(notification);
	}
	
	public List<Notification> findAllByType(NotificationType type) {
		return notificationDao.findAllByNotificationType(type);
	}

	public void delete(Notification notification) {
		notificationDao.delete(notification);
	}
}
