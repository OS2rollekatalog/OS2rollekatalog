package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;
import dk.digitalidentity.rc.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HandleInactiveRolesService {
	enum Result {
		INACTIVE, EXPIRED, ACTIVE
	}

	@Autowired
	private UserService userService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private PositionService positionService;
	
	@Autowired
	private RoleChangeInterceptor roleChangeInterceptor;

	@Transactional
	public void perform() {
		long userCount = 0, orgunitCount = 0, positionCount = 0, titleCount = 0;

		try {
			SecurityUtil.loginSystemAccount();

			// users
			List<User> users = userService.getAllIncludingInactive();
			for (User user : users) {
				boolean hasChanges = handleUserUserRoles(user);
				hasChanges = handleUserRoleGroups(user) || hasChanges;

				if (hasChanges) {
					userService.save(user);
					userCount++;
				}
			}

			// orgunits
			List<OrgUnit> orgUnits = orgUnitService.getAllIncludingInactive();
			for (OrgUnit orgUnit : orgUnits) {
				boolean hasChanges = handleOrgUnitUserRoles(orgUnit);
				hasChanges = handleOrgUnitRoleGroups(orgUnit) || hasChanges;
				
				if (hasChanges) {
					orgUnitService.save(orgUnit);
					orgunitCount++;
				}
			}

			// positions
			List<Position> positions = positionService.getAll();
			for (Position position : positions) {
				boolean hasChanges = handlePositionUserRoles(position);
				hasChanges = handlePositionRoleGroups(position) || hasChanges;
								
				if (hasChanges) {
					positionService.save(position);
					positionCount++;
				}
			}

		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}
		
		log.info("Changed assignment status on " + userCount + " users, " + orgunitCount + " orgUnits, " + titleCount + " titles, " + positionCount + " positions");
	}

	private boolean handlePositionRoleGroups(Position position) {
		List<RoleGroup> removeRoleGroups = new ArrayList<>();
		boolean hasChanges = false;

		for (PositionRoleGroupAssignment assignment : position.getRoleGroupAssignments()) {
			Result result = handle(assignment.getStartDate(), assignment.getStopDate());

			switch (result) {
				case ACTIVE:
					if (assignment.isInactive()) {
						assignment.setInactive(false);
						assignment.setStartDate(null);
						hasChanges = true;
						
						roleChangeInterceptor.interceptAddRoleGroupAssignmentOnPosition(position, assignment.getRoleGroup(), assignment.getStartDate(), assignment.getStopDate());
					}
					break;
				case EXPIRED:
					removeRoleGroups.add(assignment.getRoleGroup());
					break;
				case INACTIVE:
					break;
			}
		}

		if (removeRoleGroups.size() > 0) {
			for (RoleGroup roleGroup : removeRoleGroups) {
				positionService.removeRoleGroup(position, roleGroup);
			}
			
			hasChanges = true;
		}

		return hasChanges;
	}

	private boolean handlePositionUserRoles(Position position) {
		List<UserRole> removeUserRoles = new ArrayList<>();
		boolean hasChanges = false;

		for (PositionUserRoleAssignment assignment : position.getUserRoleAssignments()) {
			Result result = handle(assignment.getStartDate(), assignment.getStopDate());

			switch (result) {
				case ACTIVE:
					if (assignment.isInactive()) {
						assignment.setInactive(false);
						assignment.setStartDate(null);
						hasChanges = true;
						
						roleChangeInterceptor.interceptAddUserRoleAssignmentOnPosition(position, assignment.getUserRole(), assignment.getStartDate(), assignment.getStopDate());
					}
					break;
				case EXPIRED:
					removeUserRoles.add(assignment.getUserRole());
					break;
				case INACTIVE:
					break;
			}
		}

		if (removeUserRoles.size() > 0) {
			for (UserRole userRole : removeUserRoles) {
				positionService.removeUserRole(position, userRole);
			}
			
			hasChanges = true;
		}

		return hasChanges;
	}

	private boolean handleOrgUnitRoleGroups(OrgUnit orgUnit) {
		List<RoleGroup> removeRoleGroups = new ArrayList<>();
		boolean hasChanges = false;

		for (OrgUnitRoleGroupAssignment assignment : orgUnit.getRoleGroupAssignments()) {
			Result result = handle(assignment.getStartDate(), assignment.getStopDate());

			switch (result) {
				case ACTIVE:
					if (assignment.isInactive()) {
						assignment.setInactive(false);
						assignment.setStartDate(null);
						hasChanges = true;

						Set<String> titles = null;
						Set<String> exceptedUsers = null;
						if (assignment.isContainsExceptedUsers()) {
							exceptedUsers = assignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toSet());
						}
						
						if (assignment.isContainsTitles()) {
							titles = assignment.getTitles().stream().map(Title::getUuid).collect(Collectors.toSet());
						}

						roleChangeInterceptor.interceptAddRoleGroupAssignmentOnOrgUnit(orgUnit, assignment.getRoleGroup(), assignment.isInactive(), assignment.getStartDate(), assignment.getStopDate(), exceptedUsers, titles);
					}
					break;
				case EXPIRED:
					removeRoleGroups.add(assignment.getRoleGroup());
					break;
				case INACTIVE:
					break;
			}
		}

		if (removeRoleGroups.size() > 0) {
			for (RoleGroup roleGroup : removeRoleGroups) {
				orgUnitService.removeRoleGroup(orgUnit, roleGroup);
			}

			hasChanges = true;
		}
		
		return hasChanges;
	}

	private boolean handleOrgUnitUserRoles(OrgUnit orgUnit) {
		boolean hasChanges = false;

		List<UserRole> removeUserRoles = new ArrayList<>();
		for (OrgUnitUserRoleAssignment assignment : orgUnit.getUserRoleAssignments()) {
			Result result = handle(assignment.getStartDate(), assignment.getStopDate());

			switch (result) {
				case ACTIVE:
					if (assignment.isInactive()) {
						assignment.setInactive(false);
						assignment.setStartDate(null);
						hasChanges = true;

						Set<String> titles = null;
						Set<String> exceptedUsers = null;
						if (assignment.isContainsExceptedUsers()) {
							exceptedUsers = assignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toSet());
						}
						else if (assignment.isContainsTitles()) {
							titles = assignment.getTitles().stream().map(Title::getUuid).collect(Collectors.toSet());
						}

						roleChangeInterceptor.interceptAddUserRoleAssignmentOnOrgUnit(orgUnit, assignment.getUserRole(), assignment.isInherit(), assignment.getStartDate(), assignment.getStopDate(), exceptedUsers, titles);
					}
					break;
				case EXPIRED:
					removeUserRoles.add(assignment.getUserRole());
					break;
				case INACTIVE:
					break;
			}
		}

		if (removeUserRoles.size() > 0) {
			for (UserRole userRole : removeUserRoles) {
				orgUnitService.removeUserRole(orgUnit, userRole);
			}

			hasChanges = true;
		}
		
		return hasChanges;
	}

	private boolean handleUserRoleGroups(User user) {
		List<RoleGroup> removeRoleGroups = new ArrayList<>();
		boolean hasChanges = false;
		
		for (UserRoleGroupAssignment assignment : user.getRoleGroupAssignments()) {
			Result result = handle(assignment.getStartDate(), assignment.getStopDate());
			
			switch (result) {
				case ACTIVE:
					if (assignment.isInactive()) {
						assignment.setInactive(false);
						assignment.setStartDate(null);
						hasChanges = true;
						
						roleChangeInterceptor.interceptAddRoleGroupAssignmentOnUser(user, assignment.getRoleGroup(), assignment.getStartDate(), assignment.getStopDate());
					}
					break;
				case EXPIRED:
					removeRoleGroups.add(assignment.getRoleGroup());
					break;
				case INACTIVE:
					break;
			}
		}

		if (removeRoleGroups.size() > 0) {
			for (RoleGroup roleGroup : removeRoleGroups) {
				userService.removeRoleGroup(user, roleGroup);
			}
			
			hasChanges = true;
		}
		
		return hasChanges;
	}

	private boolean handleUserUserRoles(User user) {
		List<UserRole> removeUserRoles = new ArrayList<>();
		boolean hasChanges = false;

		for (UserUserRoleAssignment assignment : user.getUserRoleAssignments()) {
			Result result = handle(assignment.getStartDate(), assignment.getStopDate());
			switch (result) {
				case ACTIVE:
					if (assignment.isInactive()) {
						assignment.setInactive(false);
						assignment.setStartDate(null);
						hasChanges = true;
						
						roleChangeInterceptor.interceptAddUserRoleAssignmentOnUser(user, assignment.getUserRole(), assignment.getStartDate(), assignment.getStopDate());
					}
					break;
				case EXPIRED:
					removeUserRoles.add(assignment.getUserRole());
					break;
				case INACTIVE:
					break;
			}
		}

		if (removeUserRoles.size() > 0) {
			for (UserRole userRole : removeUserRoles) {
				userService.removeUserRole(user, userRole);
			}

			hasChanges = true;
		}

		return hasChanges;
	}

	private Result handle(LocalDate startDate, LocalDate stopDate) {
		LocalDate now = LocalDate.now();

		if (stopDate != null && now.isAfter(stopDate)) {
			return Result.EXPIRED;
		}

		if (startDate != null && now.isBefore(startDate)) {
			return Result.INACTIVE;
		}

		return Result.ACTIVE;
	}
}
