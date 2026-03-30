package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class ManualAssignmentNotificationMapService {
	private final ManualAssignmentNotificationMapDao manualAssignmentNotificationMapDao;

	public List<ManualAssignmentNotificationMap> getForRoles(Set<Long> userRoleIds) {
		return manualAssignmentNotificationMapDao.findByUserRoleIdIn(userRoleIds);
	}

	public ManualAssignmentNotificationMap save(ManualAssignmentNotificationMap map) {
		return manualAssignmentNotificationMapDao.save(map);
	}

	public void deleteAll(List<ManualAssignmentNotificationMap> maps) {
		manualAssignmentNotificationMapDao.deleteAll(maps);
	}

}
