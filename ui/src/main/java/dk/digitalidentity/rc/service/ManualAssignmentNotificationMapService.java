package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ManualAssignmentNotificationMapService {
	private final ManualAssignmentNotificationMapDao manualAssignmentNotificationMapDao;

	public List<ManualAssignmentNotificationMap> getForRoles(Set<Long> userRoleIds) {
		return manualAssignmentNotificationMapDao.findByUserRoleIdIn(userRoleIds);
	}

	public List<ManualAssignmentNotificationMap> getForUser(long domainId, String userUserId) {
		return manualAssignmentNotificationMapDao.findByDomainIdAndUserUserId(domainId, userUserId);
	}

	public List<ManualAssignmentNotificationMap> getForUsers(Collection<Long> domainIds, Collection<String> userIds) {
		return manualAssignmentNotificationMapDao.findByDomainIdInAndUserUserIdIn(domainIds, userIds);
	}

	// the removal is only detected once the notification job runs, after the person's request is long gone
	public void setRemovedBy(User user, Collection<Long> userRoleIds, String removedBy) {
		manualAssignmentNotificationMapDao.updateRemovedBy(user.getDomain().getId(), user.getUserId(), userRoleIds, removedBy);
	}

	public ManualAssignmentNotificationMap save(ManualAssignmentNotificationMap map) {
		return manualAssignmentNotificationMapDao.save(map);
	}

	public void deleteAll(List<ManualAssignmentNotificationMap> maps) {
		manualAssignmentNotificationMapDao.deleteAll(maps);
	}

}
