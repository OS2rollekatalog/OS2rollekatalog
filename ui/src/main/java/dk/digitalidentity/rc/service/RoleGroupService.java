package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoleGroupService {

    @Autowired
    private RoleGroupDao roleGroupDao;
    
	@AuditLogIntercepted
	public boolean addUserRole(RoleGroup roleGroup, UserRole userRole) {
      	if (!roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
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
      	if (roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			for (Iterator<RoleGroupUserRoleAssignment> iterator = roleGroup.getUserRoleAssignments().iterator(); iterator.hasNext();) {
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

	public List<RoleGroup> getAllRequestable() {
		return roleGroupDao.findByCanRequestTrue();
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
	
	public boolean canRequestRole(RoleGroup role, User user) {
		if (!role.isCanRequest()) {
			return false;
		}

		return true;
	}
	
	public List<RoleGroup> whichRolesCanBeRequestedByUser(List<RoleGroup> roles, User user) {

		// filter on canRequest
		roles = roles.stream().filter(r -> r.isCanRequest()).collect(Collectors.toList());

		return roles;
	}
}
