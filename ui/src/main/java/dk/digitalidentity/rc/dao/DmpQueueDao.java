package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.DmpQueue;
import dk.digitalidentity.rc.dao.model.User;

public interface DmpQueueDao extends JpaRepository<DmpQueue, Long> {
	List<DmpQueue> findAll();

	DmpQueue findByUser(User user);
}
