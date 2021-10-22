package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.PositionDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;

@Service
public class PositionService {

	@Autowired
	private PositionDao positionDao;
	
	public Position save(Position position) {
		return positionDao.save(position);
	}

	public Position getById(long positionId) {
		return positionDao.getById(positionId);
	}

	@AuditLogIntercepted
	public boolean addRoleGroup(Position position, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		if (!position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			PositionRoleGroupAssignment assignment = new PositionRoleGroupAssignment();
			assignment.setPosition(position);
			assignment.setRoleGroup(roleGroup);
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
			assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			assignment.setStopDate(stopDate);
			assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
			position.getRoleGroupAssignments().add(assignment);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeRoleGroup(Position position, RoleGroup roleGroup) {
		if (position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			for (Iterator<PositionRoleGroupAssignment> iterator = position.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
				PositionRoleGroupAssignment assignment = iterator.next();
				
				if (assignment.getRoleGroup().equals(roleGroup)) {
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean addUserRole(Position position, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan tildele Rollekatalog roller");
		}

      	if (!position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			PositionUserRoleAssignment assignment = new PositionUserRoleAssignment();
			assignment.setPosition(position);
			assignment.setUserRole(userRole);
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
			assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			assignment.setStopDate(stopDate);
			assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
			position.getUserRoleAssignments().add(assignment);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeUserRole(Position position, UserRole userRole) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
		}

      	if (position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			for (Iterator<PositionUserRoleAssignment> iterator = position.getUserRoleAssignments().iterator(); iterator.hasNext();) {
				PositionUserRoleAssignment userRoleAssignment = iterator.next();
				
				if (userRoleAssignment.getUserRole().equals(userRole)) {
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}
	
	// ONLY use this from our bulk cleanup method, which does its own auditlogging
	public boolean removeUserRolesNoAuditlog(Position position, UserRole userRole) {
		// direct access to method will bypass annotations/interceptors hence no auditLog
		return removeUserRole(position, userRole);
	}

	// ONLY use this from our bulk cleanup method, which does its own auditlogging
	public boolean removeRoleGroupsNoAuditlog(Position position, RoleGroup roleGroup) {
		// direct access to method will bypass annotations/interceptors hence no auditLog
		return removeRoleGroup(position, roleGroup);
	}

	public List<Position> getAll() {
		return positionDao.findAll();
	}
	
	public List<Position> getAllWithTitle(Title title, boolean includeInactiveUsers) {
		List<Position> positions = positionDao.findByTitle(title);
		
		if (!includeInactiveUsers) {
			positions = positions.stream().filter(p -> p.getUser().isActive()).collect(Collectors.toList());
		}

		return positions;
	}

	@Deprecated
	public List<Position> getAllWithRole(UserRole userRole) {
		return positionDao.findByUserRoleAssignmentsUserRole(userRole);
	}
	
	public List<Position> getAllWithRole(UserRole userRole, boolean inactive) {
		return positionDao.findByUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, inactive);
	}

	@Deprecated
	public List<Position> getAllWithRoleGroup(RoleGroup role) {
		return positionDao.findByRoleGroupAssignmentsRoleGroup(role);
	}

	public List<Position> getAllWithRoleGroup(RoleGroup role, boolean inactive) {
		return positionDao.findByRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(role, inactive);
	}

	public List<String> findUserUuidByOrgUnitAndActiveUsers(OrgUnit orgUnit) {
		return positionDao.findUserUuidByOrgUnit(orgUnit);
	}
	
	public List<Position> findByOrgUnit(OrgUnit ou) {
		return positionDao.findByOrgUnit(ou);
	}
}
