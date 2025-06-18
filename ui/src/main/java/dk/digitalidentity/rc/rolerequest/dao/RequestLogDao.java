package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RequestLogDao extends CrudRepository<RequestLog, Long> {
	List<RequestLog> findAllByOrderByRequestTimestampDesc();

	List<RequestLog> findByActingUser_UuidOrTargetUser_UuidOrderByRequestTimestampDesc(String actingUserUuid, String targetUserUuid);


}
