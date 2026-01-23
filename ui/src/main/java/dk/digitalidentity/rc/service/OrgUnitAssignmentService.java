package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrgUnitAssignmentService {
	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;

	public Optional<OrgUnitUserRoleAssignment> getOrgUnitUserRoleAssignment(Long assignmentId) {
		return orgUnitUserRoleAssignmentDao.findById(assignmentId);
	}

	public Optional<OrgUnitRoleGroupAssignment> getOrgUnitRoleGroupAssignment(Long assignmentId) {
		return orgUnitRoleGroupAssignmentDao.findById(assignmentId);
	}

}
