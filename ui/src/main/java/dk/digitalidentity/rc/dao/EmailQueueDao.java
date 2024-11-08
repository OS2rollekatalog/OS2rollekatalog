package dk.digitalidentity.rc.dao;

import java.util.Date;
import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.EmailQueue;


public interface EmailQueueDao extends CrudRepository<EmailQueue, Long> {
	List<EmailQueue> findByDeliveryTtsBefore(Date tts);
	long countByDeliveryTtsAfter(Date tts);
}
