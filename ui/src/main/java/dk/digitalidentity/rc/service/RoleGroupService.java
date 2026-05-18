package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupForm;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RoleGroupService {

	@Autowired
	private RoleGroupDao roleGroupDao;

	@AuditLogIntercepted
	public boolean addUserRole(RoleGroup roleGroup, UserRole userRole) {
		if (!roleGroup.getUserRoleAssignments().stream().map(RoleGroupUserRoleAssignment::getUserRole).toList().contains(userRole)) {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setUserRole(userRole);
			assignment.setRoleGroup(roleGroup);
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
			roleGroup.getUserRoleAssignments().add(assignment);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeUserRole(RoleGroup roleGroup, UserRole userRole) {
		if (roleGroup.getUserRoleAssignments().stream().map(RoleGroupUserRoleAssignment::getUserRole).toList().contains(userRole)) {
			for (Iterator<RoleGroupUserRoleAssignment> iterator = roleGroup.getUserRoleAssignments().iterator(); iterator.hasNext(); ) {
				RoleGroupUserRoleAssignment userRoleAssignment = iterator.next();

				if (userRoleAssignment.getUserRole().equals(userRole)) {
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public RoleGroup save(RoleGroup roleGroup) {
		return roleGroupDao.save(roleGroup);
	}

	@AuditLogIntercepted
	public void delete(RoleGroup roleGroup) {
		roleGroupDao.delete(roleGroup);
	}

	public List<RoleGroup> getAll() {
		return roleGroupDao.findAll();
	}

	public List<RoleGroup> getByUserRole(UserRole userRole) {
		return roleGroupDao.findByUserRoleAssignmentsUserRole(userRole);
	}


	public RoleGroup getById(long roleGroupId) {
		return roleGroupDao.findById(roleGroupId).orElse(null);
	}

	public Optional<RoleGroup> getOptionalById(long roleGroupId) {
		return roleGroupDao.findById(roleGroupId);
	}

	public Optional<RoleGroup> getByName(String name) {
		return roleGroupDao.findByName(name);
	}

	public List<RoleGroup> getRoleGroupsWithRequesterPermissions ( List<RequestableBy> permissions) {
		return roleGroupDao.findByRequesterPermissionIn(permissions);
	}
	public List<RoleGroup> getRoleGroupsWithApproverPermissions ( List<ApprovableBy> permissions) {
		return roleGroupDao.findByApproverPermissionIn(permissions);
	}

	@Transactional
	public RoleGroup copyRoleGroup(RoleGroupForm rolegroupForm, RoleGroup roleGroupToCopy) {
		RoleGroup roleGroup = new RoleGroup();

		roleGroup.setName(rolegroupForm.getName());
		roleGroup.setDescription(rolegroupForm.getDescription());

		roleGroup.setUserOnly(roleGroupToCopy.isUserOnly());
		roleGroup.setApproverPermission(roleGroupToCopy.getApproverPermission());
		roleGroup.setRequesterPermission(roleGroupToCopy.getRequesterPermission());
		roleGroup.setOuFilterEnabled(roleGroupToCopy.isOuFilterEnabled());

		// copy ou filter
		if (roleGroupToCopy.isOuFilterEnabled()) {
			roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>(roleGroupToCopy.getOrgUnitFilterOrgUnits()));
		}

		roleGroup.setUserRoleAssignments(new ArrayList<>());
		for (RoleGroupUserRoleAssignment assignmentToCopy : roleGroupToCopy.getUserRoleAssignments()) {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setRoleGroup(roleGroup);
			assignment.setUserRole(assignmentToCopy.getUserRole());
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
			roleGroup.getUserRoleAssignments().add(assignment);
		}

		roleGroup = roleGroupDao.save(roleGroup);
		return roleGroup;
	}
}
