package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.PositionDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PositionService {

	@Autowired
	private PositionDao positionDao;
	
	@Autowired
	private PositionService self;
	
	public Position save(Position position) {
		return positionDao.save(position);
	}

	public Position getById(long positionId) {
		return positionDao.getById(positionId);
	}

	@AuditLogIntercepted
	public void addRoleGroup(Position position, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		String userFullname = SecurityUtil.getUserFullname();
		String userId = SecurityUtil.getUserId();
		PositionRoleGroupAssignment assignment = new PositionRoleGroupAssignment();
		assignment.setPosition(position);
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByName(userFullname);
		assignment.setAssignedByUserId(userId);
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		assignment.setStopDateUser(userId);
		position.getRoleGroupAssignments().add(assignment);
	}

	public boolean editRoleGroup(Position position, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {

		PositionRoleGroupAssignment existingRoleGroupAssignment = position.getRoleGroupAssignments().stream().filter(ura -> ura.getRoleGroup().equals(roleGroup)).findAny().orElse(null);
		if (existingRoleGroupAssignment != null) {
			existingRoleGroupAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			existingRoleGroupAssignment.setStopDate(stopDate);
			existingRoleGroupAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
			String userId = SecurityUtil.getUserId();
			existingRoleGroupAssignment.setStopDateUser(userId);

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
					AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}
	
	@AuditLogIntercepted
	public void removeRoleGroupAssignment(Position position, PositionRoleGroupAssignment assignment) {
		for (Iterator<PositionRoleGroupAssignment> iterator = position.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
			PositionRoleGroupAssignment a = iterator.next();
			
			if (assignment.getId() == a.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
				iterator.remove();
				break;
			}
		}
	}

	public boolean removeRoleGroupAssignment(User user, long assignmentId) {
		if (user.getPositions() == null) {
			return false;
		}
		
		for (Position position : user.getPositions()) {
			for (PositionRoleGroupAssignment assignment : position.getRoleGroupAssignments()) {
				if (assignment.getId() == assignmentId) {
					self.removeRoleGroupAssignment(position, assignment);

					return true;
				}
			}
		}
		
		return false;
	}

	public void editRoleGroupAssignment(User user, PositionRoleGroupAssignment assignment, LocalDate startDate, LocalDate stopDate) {
		assignment.setStartDate(startDate);
		assignment.setStopDate(stopDate);

		String userFullname = SecurityUtil.getUserFullname();
		String userId = SecurityUtil.getUserId();
		assignment.setStopDateUser(userId);
	}

	@AuditLogIntercepted
	public void addUserRole(Position position, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan tildele Rollekatalog roller");
		}

		String userFullname = SecurityUtil.getUserFullname();
		String userId = SecurityUtil.getUserId();
		PositionUserRoleAssignment assignment = new PositionUserRoleAssignment();
		assignment.setPosition(position);
		assignment.setUserRole(userRole);
		assignment.setAssignedByName(userFullname);
		assignment.setAssignedByUserId(userId);
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setStopDateUser(userId);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		position.getUserRoleAssignments().add(assignment);
	}

	public boolean editUserRole(Position position, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan redigere Rollekatalog roller");
		}

		PositionUserRoleAssignment exitstingUserRoleAssignment = position.getUserRoleAssignments().stream().filter(ura -> ura.getUserRole().equals(userRole)).findAny().orElse(null);
		if (exitstingUserRoleAssignment != null) {
			exitstingUserRoleAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			exitstingUserRoleAssignment.setStopDate(stopDate);
			exitstingUserRoleAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

			String userId = SecurityUtil.getUserId();
			exitstingUserRoleAssignment.setStopDateUser(userId);

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
					AuditLogContextHolder.getContext().setStopDateUserId(userRoleAssignment.getStopDateUser());
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public void removeUserRoleAssignment(Position position, PositionUserRoleAssignment assignment) {
		for (Iterator<PositionUserRoleAssignment> iterator = position.getUserRoleAssignments().iterator(); iterator.hasNext();) {
			PositionUserRoleAssignment a = iterator.next();
			
			if (assignment.getId() == a.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
				iterator.remove();
				break;
			}
		}
	}

	public boolean removeUserRoleAssignment(User user, long assignmentId) {
		if (user.getPositions() == null) {
			return false;
		}
		
		for (Position position : user.getPositions()) {
			for (PositionUserRoleAssignment assignment : position.getUserRoleAssignments()) {
				if (assignment.getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
						&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
						&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
					throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
				}

				if (assignment.getId() == assignmentId) {
					self.removeUserRoleAssignment(position, assignment);

					return true;
				}
			}
		}

		return false;
	}

	@AuditLogIntercepted
	public void editUserRoleAssignment(User user, PositionUserRoleAssignment assignment, LocalDate startDate, LocalDate stopDate) {
		if (assignment.getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan redigere Rollekatalog roller");
		}

		assignment.setStartDate(startDate);
		assignment.setStopDate(stopDate);

		String userId = SecurityUtil.getUserId();
		assignment.setStopDateUser(userId);
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
	
	public List<Position> getAllWithTitle(Title title, boolean includeDeletedUsers) {
		List<Position> positions = positionDao.findByTitle(title);
		
		if (!includeDeletedUsers) {
			positions = positions.stream().filter(p -> !p.getUser().isDeleted()).collect(Collectors.toList());
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
