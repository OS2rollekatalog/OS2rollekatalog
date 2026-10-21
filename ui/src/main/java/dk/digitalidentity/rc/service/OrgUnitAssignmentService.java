package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrgUnitAssignmentService {
	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;

	@Transactional(readOnly = true)
	public Optional<OrgUnitUserRoleAssignment> getOrgUnitUserRoleAssignment(Long assignmentId) {
		return orgUnitUserRoleAssignmentDao.findById(assignmentId);
	}

	@Transactional(readOnly = true)
	public Optional<OrgUnitRoleGroupAssignment> getOrgUnitRoleGroupAssignment(Long assignmentId) {
		return orgUnitRoleGroupAssignmentDao.findById(assignmentId);
	}

	@Transactional(readOnly = true)
	public Optional<OrgUnitUserRoleAssignment> getOrgUnitUserRoleAssignment(long assignmentId, String orgUnitUuid, long userRoleId) {
		return orgUnitUserRoleAssignmentDao.findByIdAndOrgUnit_UuidAndUserRole_Id(assignmentId, orgUnitUuid, userRoleId);
	}

	@Transactional(readOnly = true)
	public Optional<OrgUnitRoleGroupAssignment> getOrgUnitRoleGroupAssignment(long assignmentId, String orgUnitUuid, long roleGroupId) {
		return orgUnitRoleGroupAssignmentDao.findByIdAndOrgUnit_UuidAndRoleGroup_Id(assignmentId, orgUnitUuid, roleGroupId);
	}

}
