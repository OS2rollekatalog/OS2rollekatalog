package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUser;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface ManualNotificationPendingUserDao extends CrudRepository<ManualNotificationPendingUser, Long> {
	Optional<ManualNotificationPendingUser> findByUserUuid(String userUuid);

	List<ManualNotificationPendingUser> findAll();
}
