package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.serializer.SystemRoleAssignmentDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@RequiredArgsConstructor
@Service
public class SystemRoleAssignmentService {
	private final SystemRoleAssignmentDao systemRoleAssignmentDao;

	public Set<SystemRoleAssignment> findAllBySystemRole(SystemRole systemRole) {
		return systemRoleAssignmentDao.findAllBySystemRole(systemRole);
	}

	public Set<SystemRoleAssignment> findAllForUserAndRolecatalogue(String userUuid) {
		return systemRoleAssignmentDao.findAllForUserInRolecatalogue(userUuid);
	}
}
