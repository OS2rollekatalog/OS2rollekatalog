package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;


public interface NotificationDao extends CrudRepository<Notification, Long> {
	List<Notification> findAll();

	Notification findById(long id);

	long countByActiveTrue();

	List<Notification> findAllByNotificationType(NotificationType type);
}
