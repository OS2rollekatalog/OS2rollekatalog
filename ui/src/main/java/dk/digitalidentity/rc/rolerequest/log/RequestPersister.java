package dk.digitalidentity.rc.rolerequest.log;

import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestPersister {

	private final RoleRequestDao roleRequestDao;

	@RequestLoggable(logEvent = RequestLogEvent.REQUEST)
	public RoleRequest saveNewRequestWithLog(RoleRequest roleRequest) {
		return roleRequestDao.save(roleRequest);
	}

	@RequestLoggable(logEvent = RequestLogEvent.REMOVE)
	public RoleRequest saveRemoveRequestWithLog(RoleRequest roleRequest) {
		return roleRequestDao.save(roleRequest);
	}
}
