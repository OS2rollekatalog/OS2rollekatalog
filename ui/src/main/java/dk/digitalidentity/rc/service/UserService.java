package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleNotAssignedDTO;
import dk.digitalidentity.rc.dao.PositionRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.PositionUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.AssignedThroughInfo;
import dk.digitalidentity.rc.service.model.AssignedThroughInfo.RoleType;
import dk.digitalidentity.rc.service.model.Constraint;
import dk.digitalidentity.rc.service.model.Privilege;
import dk.digitalidentity.rc.service.model.PrivilegeGroup;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.RoleGroupAssignmentWithInfo;
import dk.digitalidentity.rc.service.model.RoleGroupWithAssignmentIdDTO;
import dk.digitalidentity.rc.service.model.UserAssignedToRoleGroupDTO;
import dk.digitalidentity.rc.service.model.UserAssignedToUserRoleDTO;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentWithInfo;
import dk.digitalidentity.rc.service.model.UserRoleWithAssignmentIdDTO;
import dk.digitalidentity.rc.service.model.UserRoleWithPostponedConstraintDTO;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.service.model.UserWithRole2;
import dk.digitalidentity.rc.service.model.UserWithRoleAndDates;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableCaching
public class UserService {
	private static final String SELECT_THIN_USERS_SQL = "SELECT u.uuid AS uuid, u.name AS username, user_id AS userid, p.name AS title, o.name AS orgunitName, o.uuid AS orgunitUuid FROM users u JOIN positions p ON p.user_uuid = u.uuid JOIN ous o ON o.uuid = p.ou_uuid WHERE u.active = 1";

	@Autowired
	private UserDao userDao;

	@Autowired
	private KleService kleService;

	@Autowired
	private RoleGroupDao roleGroupDao;

	@Autowired
	private PositionService positionService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EmailService emailService;
	
	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private UserUserRoleAssignmentDao userUserRoleAssignmentDao;
	
	@Autowired
	private UserRoleGroupAssignmentDao userRoleGroupAssignmentDao;

	@Autowired
	private PositionUserRoleAssignmentDao positionUserRoleAssignmentDao;
	
	@Autowired
	private PositionRoleGroupAssignmentDao positionRoleGroupAssignmentDao;
	
	@Autowired
	private UserService self;

	// dao methods

	public User save(User user) {
		return userDao.save(user);
	}

	public void save(List<User> list) {
		userDao.saveAll(list);
	}

	public long countAllWithRoleGroup(RoleGroup role) {
		return userDao.countByActiveTrueAndRoleGroupAssignmentsRoleGroup(role);
	}

	public long countAllWithRole(UserRole userRole) {
		return userDao.countByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
	}

	public List<User> getByExtUuid(String uuid) {
		return userDao.getByExtUuidAndActiveTrue(uuid);
	}

	public User getByUuid(String uuid) {
		return userDao.getByUuidAndActiveTrue(uuid);
	}

	public User getByUserId(String userId) {
		return userDao.getByUserIdAndActiveTrue(userId);
	}
	
	public List<User> findByCpr(String cpr) {
		return userDao.findByCprAndActiveTrue(cpr);
	}
	
	@SuppressWarnings("deprecation")
	public List<User> getByRoleGroupsIncludingInactive(RoleGroup role) {
		return userDao.findByRoleGroupAssignmentsRoleGroup(role);
	}

	@SuppressWarnings("deprecation")
	public List<User> getByRolesIncludingInactive(UserRole userRole) {
		return userDao.findByUserRoleAssignmentsUserRole(userRole);
	}

	public List<User> getAll() {
		return userDao.getByActiveTrue();
	}
	
	@SuppressWarnings("deprecation")
	public List<User> getAllIncludingInactive() {
		return userDao.findAll();
	}
	
	@SuppressWarnings("deprecation")
	public List<User> getAllByExtUuidIncludingInactive(Set<String> extUuids) {
		return userDao.findByExtUuidIn(extUuids);
	}

	public List<User> findTop10ByName(String term) {
		return userDao.findTop10ByName(term);
	}

	public List<User> getAllInactive() {
		return userDao.getByActiveFalse();
	}

	// utility methods

	@AuditLogIntercepted
	public boolean addKLE(User user, KleType assignmentType, String code) {
		boolean found = false;

		for (UserKLEMapping kleMapping : user.getKles()) {
			if (kleMapping.getAssignmentType().equals(assignmentType) && kleMapping.getCode().equals(code)) {
				found = true;
				break;
			}
		}

		if (!found) {
			UserKLEMapping kle = new UserKLEMapping();
			kle.setUser(user);
			kle.setCode(code);
			kle.setAssignmentType(assignmentType);

			user.getKles().add(kle);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeKLE(User user, KleType assignmentType, String code) {
		UserKLEMapping existing = null;

		for (UserKLEMapping kleMapping : user.getKles()) {
			if (kleMapping.getAssignmentType().equals(assignmentType) && kleMapping.getCode().equals(code)) {
				existing = kleMapping;
				break;
			}
		}

		if (existing != null) {
			user.getKles().remove(existing);

			return true;
		}

		return false;
	}

	// this method exists solely so we can intercept it ;)
	public void activateUser(User user) {
		user.setActive(true);
	}

	public void removePosition(User user, Position position) {
		if (user.getPositions().contains(position)) {
			user.getPositions().remove(position);
		}
	}

	public void addPosition(User user, Position position) {
		if (user.getPositions() == null) {
			user.setPositions(new ArrayList<>());
		}

		if (!user.getPositions().contains(position)) {
			user.getPositions().add(position);
		}
	}

	@AuditLogIntercepted
	public void addRoleGroup(User user, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		UserRoleGroupAssignment assignment = new UserRoleGroupAssignment();
		assignment.setUser(user);
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByName(SecurityUtil.getUserFullname());
		assignment.setAssignedByUserId(SecurityUtil.getUserId());
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		user.getRoleGroupAssignments().add(assignment);
	}

	@AuditLogIntercepted
	public void editRoleGroupAssignment(User user, UserRoleGroupAssignment roleGroupAssignment, LocalDate startDate, LocalDate stopDate) {
		roleGroupAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		roleGroupAssignment.setStopDate(stopDate);
		roleGroupAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
	}

	@AuditLogIntercepted
	public boolean removeRoleGroup(User user, RoleGroup roleGroup) {
		if (user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			for (Iterator<UserRoleGroupAssignment> iterator = user.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
				UserRoleGroupAssignment assignment = iterator.next();
				
				if (assignment.getRoleGroup().equals(roleGroup)) {
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public void removeRoleGroupAssignment(User user, UserRoleGroupAssignment assignment) {
		for (Iterator<UserRoleGroupAssignment> iterator = user.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
			UserRoleGroupAssignment a = iterator.next();
			
			if (assignment.getId() == a.getId()) {
				iterator.remove();
				break;
			}
		}
	}

	public boolean removeRoleGroupAssignment(User user, long assignmentId) {
		Optional<UserRoleGroupAssignment> assignment = user.getRoleGroupAssignments().stream().filter(rga -> rga.getId() == assignmentId).findAny();
		if (assignment.isPresent()) {
			
			// trick to ensure auditlogging
			self.removeRoleGroupAssignment(user, assignment.get());
			
			return true;
		}

		return false;
	}
	
	public void addUserRole(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		self.addUserRole(user, userRole, startDate, stopDate, null);
	}

	@AuditLogIntercepted
	public void addUserRole(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate, List<PostponedConstraint> postponedConstraints) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan tildele Rollekatalog roller");
		}

		UserUserRoleAssignment assignment = new UserUserRoleAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setAssignedByName(SecurityUtil.getUserFullname());
		assignment.setAssignedByUserId(SecurityUtil.getUserId());
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		
		
		if (postponedConstraints != null) {
			for (PostponedConstraint postponedConstraint : postponedConstraints) {
				postponedConstraint.setUserUserRoleAssignment(assignment);
			}
		}
		
		assignment.setPostponedConstraints(postponedConstraints);
		
		user.getUserRoleAssignments().add(assignment);
	}

	@AuditLogIntercepted
	public void editUserRoleAssignment(User user, UserUserRoleAssignment userRoleAssignment, LocalDate startDate, LocalDate stopDate) {
		editUserRoleAssignment(user, userRoleAssignment, startDate, stopDate, null);
	}

	@AuditLogIntercepted
	public void editUserRoleAssignment(User user, UserUserRoleAssignment userRoleAssignment, LocalDate startDate, LocalDate stopDate, List<PostponedConstraint> postponedConstraints) {
		if (userRoleAssignment.getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan redigere Rollekatalog roller");
		}

		userRoleAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		userRoleAssignment.setStopDate(stopDate);
		userRoleAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

		if (postponedConstraints != null) {
			userRoleAssignment.getPostponedConstraints().removeAll(userRoleAssignment.getPostponedConstraints());
				
			for (PostponedConstraint postponedConstraint : postponedConstraints) {
				postponedConstraint.setUserUserRoleAssignment(userRoleAssignment);
				userRoleAssignment.getPostponedConstraints().add(postponedConstraint);
			}
		}
	}

	@AuditLogIntercepted
	public boolean removeUserRole(User user, UserRole userRole) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
		}

		if (user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {

			for (Iterator<UserUserRoleAssignment> iterator = user.getUserRoleAssignments().iterator(); iterator.hasNext();) {
				UserUserRoleAssignment userRoleAssignment = iterator.next();
				
				if (userRoleAssignment.getUserRole().equals(userRole)) {
					iterator.remove();
				}
			}

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public void removeUserRoleAssignment(User user, UserUserRoleAssignment assignment) {
		for (Iterator<UserUserRoleAssignment> iterator = user.getUserRoleAssignments().iterator(); iterator.hasNext();) {
			UserUserRoleAssignment a = iterator.next();
			
			if (assignment.getId() == a.getId()) {
				iterator.remove();
				break;
			}
		}
	}

	public boolean removeUserRoleAssignment(User user, long assignmentId) {
		Optional<UserUserRoleAssignment> assignment = user.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignmentId).findAny();

		if (assignment.isPresent()) {
			if (assignment.get().getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
					&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
					&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
				throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
			}

			// ensure auditlogging like this
			self.removeUserRoleAssignment(user, assignment.get());

			return true;
		}

		return false;
	}

	// TODO: this method is only used by the ReadOnlyApi, and very likely noone has any use for
	//       this method, so deprecate it in future versions of the API
	public List<UserRole> getUserRolesAssignedDirectly(String id) throws UserNotFoundException {
		User user = userDao.getByUuidAndActiveTrue(id);
		if (user == null) {
			User byUserId = userDao.getByUserIdAndActiveTrue(id);

			if (byUserId == null) {
				throw new UserNotFoundException("User with id '" + id + "' was not found in the database");
			}
			user = byUserId;
		}

		return user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
	}

	// TODO: this method is only used by the ReadOnlyApi, and very likely noone has any use for
	//       this method, so deprecate it in future versions of the API
	public List<RoleGroup> getRoleGroupsAssignedDirectly(String uuid) throws UserNotFoundException {
		User user = userDao.getByUuidAndActiveTrue(uuid);
		if (user == null) {
			throw new UserNotFoundException("User with uuid '" + uuid + "' was not found in the database");
		}

		return user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList());
	}

	public String getUserNameId(String userId) throws UserNotFoundException {
		User user = userDao.getByUserIdAndActiveTrue(userId);
		if (user == null) {
			List<User> users = userDao.getByExtUuidAndActiveTrue(userId);
			if (users.size() != 1) {
				throw new UserNotFoundException("User with userId '" + userId + "' was not found in the database");
			}
			user = users.get(0);
		}

		return "C=DK,O=" + configuration.getCustomer().getCvr() + ",CN=" + user.getName() + ",Serial=" + user.getExtUuid();
	}

	/**
	 * Returns all assigned system roles, no matter how they are assigned to the user
	 */
	public List<SystemRole> getAllSystemRoles(User user, List<ItSystem> itSystems) {
		List<UserRole> userRoles = getAllUserRoles(user, itSystems);
		
		Set<SystemRole> systemRoles = new HashSet<>();
		for (UserRole userRole : userRoles) {
			systemRoles.addAll(userRole.getSystemRoleAssignments()
					.stream()
					.map(sra -> sra.getSystemRole())
					.collect(Collectors.toList()));
		}
		
		return new ArrayList<>(systemRoles);
	}
	
	/**
	 * Returns all UserRoles, no matter how they are assigned to the user
	 */
	public List<UserRole> getAllUserRoles(String userId, List<ItSystem> itSystems) throws UserNotFoundException {
		User user = userDao.getByUserIdAndActiveTrue(userId);
		if (user == null) {
			throw new UserNotFoundException("User with userId '" + userId + "' was not found in the database");
		}

		return getAllUserRoles(user, itSystems);
	}

	/**
	 * Returns all UserRoles, no matter how they are assigned to the user
	 */
	public List<UserRole> getAllUserRoles(User user, List<ItSystem> itSystems) {
		Set<UserRole> resultSet = new HashSet<>();

		for (ItSystem itSystem : itSystems) {
			List<UserRoleAssignedToUser> result = getAllUserRolesAssignedToUser(user, itSystem);
			
			// stream to Set to ensure uniqueness
			resultSet.addAll(result.stream().map(a -> a.getUserRole()).collect(Collectors.toSet()));
		}
		
		return new ArrayList<>(resultSet);
	}
	
	public List<UserRoleAssignmentWithInfo> getAllUserRolesAssignmentsWithInfo(User user, List<ItSystem> itSystems) {
		List<UserRoleAssignmentWithInfo> result = new ArrayList<>();

		for (ItSystem itSystem : itSystems) {
			List<UserRoleAssignmentWithInfo> assignments = getAllUserRolesAssignedToUserWithInfo(user, itSystem, true);
			
			result.addAll(assignments);
		}
		
		return result;
	}

	public void deleteDuplicateUserRoleAssignmentsOnUsers() {
		User admin = getByUserId(SecurityUtil.getUserId());
		if (admin == null) {
			throw new RuntimeException("No administrator logged in");
		}
		
		Map<User, List<UserRoleAssignmentWithInfo>> usersWithRoleAssignments = getUsersWithRoleAssignments();

		List<User> dirtyUsers = new ArrayList<>();
		for (User user : usersWithRoleAssignments.keySet()) {
			List<UserRoleAssignmentWithInfo> assignments = usersWithRoleAssignments.get(user);
			if (assignments == null || assignments.size() == 0) {
				continue;
			}
			
			List<UserRoleAssignmentWithInfo> directAssignments = assignments.stream().filter(a -> a.getAssignedThroughInfo() == null).collect(Collectors.toList());
			if (directAssignments == null || directAssignments.size() == 0) {
				continue;
			}

			boolean dirty = false;
			for (UserRoleAssignmentWithInfo directAssignment : directAssignments) {
				boolean duplicate = false;

				for (UserRoleAssignmentWithInfo assignment : assignments) {
					// ignore direct assignments
					if (assignment.getAssignedThroughInfo() == null) {
						continue;
					}
					
					// is this UserRole also assigned indirectly?
					if (assignment.getUserRole().getId() == directAssignment.getUserRole().getId()) {
						duplicate = true;
						break;
					}
				}
				
				// remove duplicates
				if (duplicate) {
					UserRole role = directAssignment.getUserRole();
					
					dirty = dirty | removeUserRole(user, role);

					// remove through positions if not assigned directly
					if (!dirty) {
						for (Position p : user.getPositions()) {
							dirty = dirty | positionService.removeUserRolesNoAuditlog(p, role);
						}
					}
				}
			}

			if (dirty) {
				dirtyUsers.add(user);
			}
		}
		
		if (dirtyUsers.size() > 0) {
			auditLogger.log(admin, EventType.PERFORMED_USERROLE_CLEANUP);
			
			userDao.saveAll(dirtyUsers);
		}
	}

	public void deleteDuplicateRoleGroupAssignmentsOnUsers() {
		User admin = getByUserId(SecurityUtil.getUserId());
		if (admin == null) {
			throw new RuntimeException("No administrator logged in");
		}
		
		Map<User, List<RoleGroupAssignmentWithInfo>> usersWithRoleGroupAssignments = getUsersWithRoleGroupAssignments();

		List<User> dirtyUsers = new ArrayList<>();
		for (User user : usersWithRoleGroupAssignments.keySet()) {
			List<RoleGroupAssignmentWithInfo> assignments = usersWithRoleGroupAssignments.get(user);
			if (assignments == null || assignments.size() == 0) {
				continue;
			}
			
			List<RoleGroupAssignmentWithInfo> directAssignments = assignments.stream().filter(a -> a.getAssignedThroughInfo() == null).collect(Collectors.toList());
			if (directAssignments == null || directAssignments.size() == 0) {
				continue;
			}

			boolean dirty = false;
			for (RoleGroupAssignmentWithInfo directAssignment : directAssignments) {
				boolean duplicate = false;

				for (RoleGroupAssignmentWithInfo assignment : assignments) {
					// ignore direct assignments
					if (assignment.getAssignedThroughInfo() == null) {
						continue;
					}
					
					// is this RoleGroup also assigned indirectly?
					if (assignment.getRoleGroup().getId() == directAssignment.getRoleGroup().getId()) {
						duplicate = true;
						break;
					}
				}
				
				// remove duplicates
				if (duplicate) {
					RoleGroup roleGroup = directAssignment.getRoleGroup();
					
					// remove direct assignment (self. to ensure auditlogging)
					//self.removeUserRole(user, role);
					dirty = dirty | removeRoleGroup(user, roleGroup);

					// remove through positions if not assigned directly
					if (!dirty) {
						for (Position p : user.getPositions()) {
							dirty = dirty | positionService.removeRoleGroupsNoAuditlog(p, roleGroup);
						}
					}
				}
			}

			if (dirty) {
				dirtyUsers.add(user);
			}
		}
		
		if (dirtyUsers.size() > 0) {
			auditLogger.log(admin, EventType.PERFORMED_ROLEGROUP_CLEANUP);
			
			userDao.saveAll(dirtyUsers);
		}
	}

	public Map<User, List<UserRoleAssignmentWithInfo>> getUsersWithRoleAssignments() {
		Map<User, List<UserRoleAssignmentWithInfo>> result = new HashMap<>();
		List<User> users = getAll();
		
		for (User user : users) {
			List<UserRoleAssignmentWithInfo> assignments = getAllUserRolesAssignedToUserWithInfo(user, null, true);
			result.put(user, assignments);
		}
		
		return result;
	}

	public Map<User, List<RoleGroupAssignmentWithInfo>> getUsersWithRoleGroupAssignments() {
		Map<User, List<RoleGroupAssignmentWithInfo>> result = new HashMap<>();
		List<User> users = getAll();
		
		for (User user : users) {
			List<RoleGroupAssignmentWithInfo> assignments = getAllRoleGroupsAssignedToUserWithInfo(user);
			result.put(user, assignments);
		}
		
		return result;
	}
	
	/**
	 * Returns all UserRoles, no matter how they are assigned to the user
	 */
	public List<UserRoleAssignedToUser> getAllUserRolesAssignedToUser(User user, ItSystem itSystem) {
		return getAllUserRolesAssignedToUser(user, itSystem, true);
	}

	/**
	 * Returns all UserRoles, no matter how they are assigned to the user (except those embeded inside RoleGroups)
	 */
	public List<UserRoleAssignedToUser> getAllUserRolesAssignedToUserExemptingRoleGroups(User user, ItSystem itSystem) {
		return getAllUserRolesAssignedToUser(user, itSystem, false);
	}

	private List<UserRoleAssignedToUser> getAllUserRolesAssignedToUser(User user, ItSystem itSystem, boolean expandRoleGroups) {
		List<UserRoleAssignedToUser> result = new ArrayList<>();

		List<UserRoleAssignmentWithInfo> assignments = getAllUserRolesAssignedToUserWithInfo(user, itSystem, expandRoleGroups);
		for (UserRoleAssignmentWithInfo assignment : assignments) {
			result.add(assignment.toUserRoleAssignedToUser());
		}

		return result;
	}

	public List<UserRoleAssignmentWithInfo> getAllUserRolesAssignedToUserWithInfo(User user, ItSystem itSystem, boolean expandRoleGroups) {
		List<UserRoleAssignmentWithInfo> result = new ArrayList<>();

		// user.getUserRoles()
		List<UserRoleWithPostponedConstraintDTO> userRoles = user.getUserRoleAssignments().stream()
				.filter(ura -> !ura.isInactive())
				.map(ura -> new UserRoleWithPostponedConstraintDTO(ura))
				.collect(Collectors.toList());

		for (UserRoleWithPostponedConstraintDTO roleWithConstraint : userRoles) {
			UserRole role = roleWithConstraint.getUserRole();
			if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
				UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
				assignment.setUserRole(role);
				assignment.setPostponedConstraints(roleWithConstraint.getPostponedConstraints());
				assignment.setAssignmentId(roleWithConstraint.getAssignmentId());

				result.add(assignment);
			}
		}

		// user.getRoleGroups()
		if (expandRoleGroups) {
			List<RoleGroup> roleGroups = user.getRoleGroupAssignments().stream()
					.filter(ura -> !ura.isInactive())
					.map(ura -> ura.getRoleGroup())
					.collect(Collectors.toList());
	
			for (RoleGroup roleGroup : roleGroups) {
				List<UserRoleWithAssignmentIdDTO> ur = roleGroup.getUserRoleAssignments().stream().map(ura -> new UserRoleWithAssignmentIdDTO(ura)).collect(Collectors.toList());
	
				for (UserRoleWithAssignmentIdDTO roleWithId : ur) {
					UserRole role = roleWithId.getUserRole();
					if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
						assignment.setUserRole(role);
						assignment.setAssignedThroughInfo(new AssignedThroughInfo(roleGroup));
						assignment.setAssignmentId(roleWithId.getAssignmentId());
	
						result.add(assignment);
					}
				}
			}
		}

		// user.getPositions() -> everything assigned through these
		for (Position position : user.getPositions()) {

			// position.getRoles();
			List<UserRoleWithAssignmentIdDTO> pur = position.getUserRoleAssignments().stream()
					.filter(ura -> !ura.isInactive())
					.map(ura -> new UserRoleWithAssignmentIdDTO(ura))
					.collect(Collectors.toList());

			for (UserRoleWithAssignmentIdDTO roleWithId : pur) {
				UserRole role = roleWithId.getUserRole();
				if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
					UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
					assignment.setUserRole(role);
					assignment.setAssignmentId(roleWithId.getAssignmentId());
					assignment.setFromPosition(true);
					
					result.add(assignment);
				}
			}

			// position.getRoleGroups
			if (expandRoleGroups) {
				List<RoleGroup> prg = position.getRoleGroupAssignments().stream()
						.filter(ura -> !ura.isInactive())
						.map(ura -> ura.getRoleGroup())
						.collect(Collectors.toList());
	
				for (RoleGroup roleGroup : prg) {
					List<UserRoleWithAssignmentIdDTO> rur = roleGroup.getUserRoleAssignments().stream().map(ura -> new UserRoleWithAssignmentIdDTO(ura)).collect(Collectors.toList());
	
					for (UserRoleWithAssignmentIdDTO roleWithId : rur) {
						UserRole role = roleWithId.getUserRole();
						if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
							UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
							assignment.setUserRole(role);
							assignment.setAssignedThroughInfo(new AssignedThroughInfo(roleGroup));
							assignment.setAssignmentId(roleWithId.getAssignmentId());
							assignment.setFromPosition(true);
	
							result.add(assignment);
						}
					}
				}
			}
			
			if (!user.isDoNotInherit() && position.getTitle() != null) {

				// position.title (userRoles)
				
				List<OrgUnitUserRoleAssignment> assignments = position.getOrgUnit().getUserRoleAssignments()
					.stream()
					.filter(ura -> ura.isContainsTitles() && ura.getTitles().contains(position.getTitle()))
					.collect(Collectors.toList());
				
				for (OrgUnitUserRoleAssignment assignment : assignments) {
					if (assignment.isInactive()) {
						continue;
					}

					if (itSystem == null || assignment.getUserRole().getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignmentWithInfo uratu = new UserRoleAssignmentWithInfo();
						uratu.setUserRole(assignment.getUserRole());
						uratu.setTitle(position.getTitle());
						uratu.setOrgUnit(position.getOrgUnit());
						uratu.setAssignedThroughInfo(new AssignedThroughInfo(position.getOrgUnit(), position.getTitle(), RoleType.USERROLE));
						uratu.setAssignmentId(assignment.getId());
						
						result.add(uratu);
					}
				}
				
				// position.title (RoleGroups)
				if (expandRoleGroups) {
					
					List<OrgUnitRoleGroupAssignment> roleGroupAssignments = position.getOrgUnit().getRoleGroupAssignments()
							.stream()
							.filter(ura -> ura.isContainsTitles() && ura.getTitles().contains(position.getTitle()))
							.collect(Collectors.toList());
						
					for (OrgUnitRoleGroupAssignment assignment : roleGroupAssignments) {
						if (assignment.isInactive()) {
							continue;
						}
						List<UserRoleWithAssignmentIdDTO> rur = assignment.getRoleGroup().getUserRoleAssignments().stream().map(ura -> new UserRoleWithAssignmentIdDTO(ura)).collect(Collectors.toList());
						for (UserRoleWithAssignmentIdDTO roleWithId : rur) {
							UserRole role = roleWithId.getUserRole();
							if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
								UserRoleAssignmentWithInfo uratu = new UserRoleAssignmentWithInfo();
								uratu.setUserRole(role);
								uratu.setTitle(position.getTitle());
								uratu.setOrgUnit(position.getOrgUnit());
								uratu.setAssignedThroughInfo(new AssignedThroughInfo(position.getOrgUnit(),position.getTitle(), RoleType.ROLEGROUP));
								uratu.setAssignmentId(roleWithId.getAssignmentId());
								
								result.add(uratu);
							}
						}
					}
					
				}
			}

			if (!user.isDoNotInherit()) {
				// recursive through all OrgUnits from here and up
				getAllUserRolesFromOrgUnit(result, position.getOrgUnit(), itSystem, false, expandRoleGroups, user);
			}
		}

		return new ArrayList<>(result);
	}

	/**
	 * Finds all UserRole and RoleGroup assignments for given User. Used in new Manage UI
	 */
	public List<RoleAssignedToUserDTO> getAllUserRoleAndRoleGroupAssignments(User user) {
		List<RoleAssignedToUserDTO> result = new ArrayList<>();

		// user.getUserRoles()
		List<UserUserRoleAssignment> userRoleAssignments = user.getUserRoleAssignments().stream().collect(Collectors.toList());
		
		result.addAll(userRoleAssignments.stream().map(RoleAssignedToUserDTO::fromUserRoleAssignment).collect(Collectors.toList()));

		// user.getRoleGroups()
		List<UserRoleGroupAssignment> roleGroupAssignments = user.getRoleGroupAssignments().stream().collect(Collectors.toList());

		result.addAll(roleGroupAssignments.stream().map(RoleAssignedToUserDTO::fromRoleGroupAssignment).collect(Collectors.toList()));

		// user.getPositions() -> everything assigned through these
		for (Position position : user.getPositions()) {

			// position.getRoles();
			List<PositionUserRoleAssignment> pur = position.getUserRoleAssignments().stream().collect(Collectors.toList());

			result.addAll(pur.stream().map(RoleAssignedToUserDTO::fromPositionUserRoleAssignment).collect(Collectors.toList()));

			// position.getRoleGroups
			List<PositionRoleGroupAssignment> prg = position.getRoleGroupAssignments().stream().collect(Collectors.toList());
			
			result.addAll(prg.stream().map(RoleAssignedToUserDTO::fromPositionRoleGroupAssignment).collect(Collectors.toList()));

			if (!user.isDoNotInherit()) {
				// recursive through all OrgUnits from here and up
				getAllUserRolesAndRoleGroupsFromOrgUnit(result, position.getOrgUnit(), false, user, position.getTitle());
			}
		}

		// TODO: this should be temporary code - we need it for now to support the old way of using the UI, but
		// eventually we should look into a redesign of the UI for UserRoles within RoleGroups

		// expand rolegroups
		List<RoleAssignedToUserDTO> expanded = new ArrayList<>();
		for (RoleAssignedToUserDTO assignment : result) {
			if (assignment.getType().equals(RoleAssignmentType.ROLEGROUP)) {
				RoleGroup roleGroup = roleGroupDao.findById(assignment.getRoleId());

				if (roleGroup != null && roleGroup.getUserRoleAssignments() != null) {
					for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
						expanded.add(RoleAssignedToUserDTO.fromRoleGroupUserRoleAssignment(userRoleAssignment, assignment.getStartDate(), assignment.getStopDate()));
					}
				}
			}
		}

		result.addAll(expanded);
		
		return new ArrayList<>(result);
	}

	private void getAllUserRolesAndRoleGroupsFromOrgUnit(List<RoleAssignedToUserDTO> result, OrgUnit orgUnit, boolean inheritOnly, User user, Title title) {

		// ou.getRoles()
		for (OrgUnitUserRoleAssignment roleMapping : orgUnit.getUserRoleAssignments()) {

			if (inheritOnly && !roleMapping.isInherit()) {
				continue;
			}

			// Filter users which are excepted from the assignment
			if (roleMapping.isContainsExceptedUsers()) {
				Optional<User> match = roleMapping.getExceptedUsers()
						.stream()
						.filter(u -> Objects.equals(u.getUuid(), user.getUuid()))
						.findAny();

				if (match.isPresent()) {
					continue;
				}
			}

			// Filter out assignments through title that don't include the user's title
			if (roleMapping.isContainsTitles() && !roleMapping.getTitles().contains(title)) {
				continue;
			}
			
			result.add(RoleAssignedToUserDTO.fromOrgUnitUserRoleAssignment(roleMapping));
		}

		// ou.getRoleGroups()
		for (OrgUnitRoleGroupAssignment roleGroupMapping : orgUnit.getRoleGroupAssignments()) {

			if (inheritOnly && !roleGroupMapping.isInherit()) {
				continue;
			}
	
			// Filter users which are excepted from the assignment
			if (roleGroupMapping.isContainsExceptedUsers()) {
				Optional<User> match = roleGroupMapping.getExceptedUsers()
						.stream()
						.filter(u -> Objects.equals(u.getUuid(), user.getUuid()))
						.findAny();
	
				if (match.isPresent()) {
					continue;
				}
			}

			// Filter out assignments through title that don't include the user's title
			if (roleGroupMapping.isContainsTitles() && !roleGroupMapping.getTitles().contains(title)) {
				continue;
			}
	
			result.add(RoleAssignedToUserDTO.fromOrgUnitRoleGroupAssignment(roleGroupMapping));
		}
		
		// recursive upwards in the hierarchy (only inherited roles)
		if (orgUnit.getParent() != null) {
			getAllUserRolesAndRoleGroupsFromOrgUnit(result, orgUnit.getParent(), true, user, title);
		}
	}

	public List<UserRoleNotAssignedDTO> getAllExceptedUserRolesForUser(User user) {

		ArrayList<UserRoleNotAssignedDTO> result = new ArrayList<>();
		for (Position position : user.getPositions()) {
			OrgUnit orgUnit = position.getOrgUnit();

			// UserRoles
			List<OrgUnitUserRoleAssignment> userRolesWithExceptions = orgUnit.getUserRoleAssignments().stream().filter(OrgUnitUserRoleAssignment::isContainsExceptedUsers).collect(Collectors.toList());
			for (OrgUnitUserRoleAssignment userRoleAssignment : userRolesWithExceptions) {
				for (User exceptedUser : userRoleAssignment.getExceptedUsers()) {
					if (Objects.equals(exceptedUser.getUuid(), user.getUuid())) {
						result.add(new UserRoleNotAssignedDTO(userRoleAssignment.getUserRole(), orgUnit));
					}
				}
			}

			// RoleGroups
			List<OrgUnitRoleGroupAssignment> roleGroupsWithExceptions = orgUnit.getRoleGroupAssignments().stream().filter(OrgUnitRoleGroupAssignment::isContainsExceptedUsers).collect(Collectors.toList());
			for (OrgUnitRoleGroupAssignment roleGroupWithException : roleGroupsWithExceptions) {
				for (User exceptedUser : roleGroupWithException.getExceptedUsers()) {
					if (Objects.equals(exceptedUser.getUuid(), user.getUuid())) {
						result.addAll(roleGroupWithException.getRoleGroup().getUserRoleAssignments().stream().map(ura -> new UserRoleNotAssignedDTO(ura.getUserRole(), orgUnit)).collect(Collectors.toList()));
					}
				}
			}
		}

		return result;
	}
	/**
	 * Returns all UserRoles, no matter how they are assigned to the user
	 */
	public List<RoleGroupAssignedToUser> getAllRoleGroupsAssignedToUser(User user) {
		List<RoleGroupAssignedToUser> result= new ArrayList<>();

		List<RoleGroupAssignmentWithInfo> assignments = getAllRoleGroupsAssignedToUserWithInfo(user);
		for (RoleGroupAssignmentWithInfo assignment : assignments) {
			result.add(assignment.toRoleGroupAssignment());
		}

		return result;
	}

	public List<RoleGroupAssignmentWithInfo> getAllRoleGroupsAssignedToUserWithInfo(User user) {
		List<RoleGroupAssignmentWithInfo> result = new ArrayList<>();

		// user.getRoleGroups()
		List<RoleGroupWithAssignmentIdDTO> roleGroups = user.getRoleGroupAssignments().stream()
				.filter(ura -> !ura.isInactive())
				.map(ura -> new RoleGroupWithAssignmentIdDTO(ura))
				.collect(Collectors.toList());

		for (RoleGroupWithAssignmentIdDTO roleGroupWithId : roleGroups) {
			RoleGroup roleGroup = roleGroupWithId.getRoleGroup();
			RoleGroupAssignmentWithInfo assignment = new RoleGroupAssignmentWithInfo();
			assignment.setRoleGroup(roleGroup);
			assignment.setAssignmentId(roleGroupWithId.getAssignmentId());

			result.add(assignment);
		}

		// user.getPositions() -> everything assigned through these
		for (Position position : user.getPositions()) {

			// position.getRoleGroups
	      	List<RoleGroupWithAssignmentIdDTO> prg = position.getRoleGroupAssignments().stream()
					.filter(ura -> !ura.isInactive())
	      			.map(ura -> new RoleGroupWithAssignmentIdDTO(ura))
	      			.collect(Collectors.toList());
	      	
			for (RoleGroupWithAssignmentIdDTO roleGroupWithId : prg) {
				RoleGroup roleGroup = roleGroupWithId.getRoleGroup();
				RoleGroupAssignmentWithInfo assignment = new RoleGroupAssignmentWithInfo();
				assignment.setRoleGroup(roleGroup);
				assignment.setAssignedThroughInfo(new AssignedThroughInfo(roleGroup));
				assignment.setAssignmentId(roleGroupWithId.getAssignmentId());
				assignment.setFromPosition(true);

				result.add(assignment);
			}

			if (!user.isDoNotInherit() && position.getTitle() != null) {

				// position.title (RoleGroups)
				List<OrgUnitRoleGroupAssignment> titleAssignments = position.getOrgUnit().getRoleGroupAssignments()
						.stream()
						.filter(ura -> ura.isContainsTitles() && ura.getTitles().contains(position.getTitle()))
						.collect(Collectors.toList());
				
				for (OrgUnitRoleGroupAssignment assignment : titleAssignments) {
					if (assignment.isInactive()) {
						continue;
					}

					RoleGroupAssignmentWithInfo rgatu = new RoleGroupAssignmentWithInfo();
					rgatu.setRoleGroup(assignment.getRoleGroup());
					rgatu.setAssignedThroughInfo(new AssignedThroughInfo(position.getOrgUnit(), position.getTitle(), RoleType.ROLEGROUP));
					rgatu.setTitle(position.getTitle());
					rgatu.setOrgUnit(position.getOrgUnit());
					rgatu.setAssignmentId(assignment.getId());

					result.add(rgatu);
				}
			}

			// recursive through all OrgUnits from here and up
			if (!user.isDoNotInherit()) {
				getAllRoleGroupsFromOrgUnit(result, position.getOrgUnit(), false, user);
			}
		}

		return new ArrayList<>(result);
	}
	
	private void getAllRoleGroupsFromOrgUnit(List<RoleGroupAssignmentWithInfo> result, OrgUnit orgUnit, boolean inheritOnly, User user) {

		// ou.getRoleGroups()
		for (OrgUnitRoleGroupAssignment roleGroupMapping : orgUnit.getRoleGroupAssignments()) {
			if (inheritOnly && !roleGroupMapping.isInherit()) {
				continue;
			}

			// Filter users which are excepted from the assignment
			if (roleGroupMapping.isContainsExceptedUsers()) {
				Optional<User> match = roleGroupMapping.getExceptedUsers()
						.stream()
						.filter(u -> Objects.equals(u.getUuid(), user.getUuid()))
						.findAny();

				if (match.isPresent()) {
					continue;
				}
			}
			
			if (roleGroupMapping.isContainsTitles()) {
				boolean match = roleGroupMapping.getTitles()
						.stream()
						.anyMatch(t -> user.getPositions()
								.stream()
								.anyMatch(p -> p.getTitle() != null && Objects.equals(p.getTitle().getUuid(), t.getUuid()))
						);

				if (!match) {
					continue;
				}
			}
			
			RoleGroupAssignmentWithInfo assignment = new RoleGroupAssignmentWithInfo();
			assignment.setRoleGroup(roleGroupMapping.getRoleGroup());
			assignment.setAssignedThroughInfo(new AssignedThroughInfo(orgUnit, RoleType.ROLEGROUP));
			assignment.setOrgUnit(orgUnit);
			assignment.setAssignmentId(roleGroupMapping.getId());

			result.add(assignment);
		}
		
		// recursive upwards in the hierarchy (only inherited roles)
		if (orgUnit.getParent() != null) {
			getAllRoleGroupsFromOrgUnit(result, orgUnit.getParent(), true, user);
		}
	}

	private void getAllUserRolesFromOrgUnit(List<UserRoleAssignmentWithInfo> result, OrgUnit orgUnit, ItSystem itSystem, boolean inheritOnly, boolean expandRoleGroups, User user) {

		// ou.getRoles()
		for (OrgUnitUserRoleAssignment roleMapping : orgUnit.getUserRoleAssignments()) {
			if (roleMapping.isInactive()) {
				continue;
			}

			if (inheritOnly && !roleMapping.isInherit()) {
				continue;
			}

			// Filter users which are excepted from the assignment
			if (roleMapping.isContainsExceptedUsers()) {
				Optional<User> match = roleMapping.getExceptedUsers()
						.stream()
						.filter(u -> Objects.equals(u.getUuid(), user.getUuid()))
						.findAny();

				if (match.isPresent()) {
					continue;
				}
			}
			
			if (roleMapping.isContainsTitles()) {
				boolean match = roleMapping.getTitles()
						.stream()
						.anyMatch(t -> user.getPositions()
								.stream()
								.anyMatch(p -> p.getTitle() != null && Objects.equals(p.getTitle().getUuid(), t.getUuid()))
						);

				if (!match) {
					continue;
				}
			}
			
			UserRole role = roleMapping.getUserRole();

			if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
				UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
				assignment.setUserRole(role);
				assignment.setOrgUnit(orgUnit);
				assignment.setAssignedThroughInfo(new AssignedThroughInfo(orgUnit, RoleType.USERROLE));
				assignment.setAssignmentId(roleMapping.getId());
				
				result.add(assignment);
			}
		}

		// ou.getRoleGroups()
		if (expandRoleGroups) {
			for (OrgUnitRoleGroupAssignment roleGroupMapping : orgUnit.getRoleGroupAssignments()) {
				if (roleGroupMapping.isInactive()) {
					continue;
				}
	
				if (inheritOnly && !roleGroupMapping.isInherit()) {
					continue;
				}

				// Filter users which are excepted from the assignment
				if (roleGroupMapping.isContainsExceptedUsers()) {
					Optional<User> match = roleGroupMapping.getExceptedUsers()
							.stream()
							.filter(u -> Objects.equals(u.getUuid(), user.getUuid()))
							.findAny();

					if (match.isPresent()) {
						continue;
					}
				}

				if (roleGroupMapping.isContainsTitles()) {
					boolean match = roleGroupMapping.getTitles()
							.stream()
							.anyMatch(t -> user.getPositions()
									.stream()
									.anyMatch(p -> p.getTitle() != null && Objects.equals(p.getTitle().getUuid(), t.getUuid()))
							);

					if (!match) {
						continue;
					}
				}

				RoleGroup roleGroup = roleGroupMapping.getRoleGroup();
				
				List<UserRoleWithAssignmentIdDTO> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> new UserRoleWithAssignmentIdDTO(ura)).collect(Collectors.toList());
				for (UserRoleWithAssignmentIdDTO roleWithId : userRoles) {
					UserRole role = roleWithId.getUserRole();
					if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
						assignment.setUserRole(role);
						assignment.setOrgUnit(orgUnit);
						assignment.setAssignedThroughInfo(new AssignedThroughInfo(orgUnit, RoleType.ROLEGROUP));
						assignment.setAssignmentId(roleWithId.getAssignmentId());
						result.add(assignment);
					}
				}
			}
		}
		
		// recursive upwards in the hierarchy (only inherited roles)
		if (orgUnit.getParent() != null) {
			getAllUserRolesFromOrgUnit(result, orgUnit.getParent(), itSystem, true, expandRoleGroups, user);
		}
	}

	public List<UserWithRole2> getActiveUsersWithRoleGroup(RoleGroup roleGroup) {
		List<UserWithRole2> result = new ArrayList<>();
		
		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		for (UserRoleGroupAssignment assignment : userRoleGroupAssignmentDao.findByRoleGroup(roleGroup)) {
			if (!assignment.getUser().isActive()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(assignment.getUser().getName());
			mapping.setUserUserId(assignment.getUser().getUserId());
			mapping.setUserUuid(assignment.getUser().getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromRoleGroupAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);
		}
		
		for (PositionRoleGroupAssignment assignment : positionRoleGroupAssignmentDao.findByRoleGroup(roleGroup)) {
			if (!assignment.getPosition().getUser().isActive()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(assignment.getPosition().getUser().getName());
			mapping.setUserUserId(assignment.getPosition().getUser().getUserId());
			mapping.setUserUuid(assignment.getPosition().getUser().getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromPositionRoleGroupAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);			
		}

		return result;
	}
	
	public List<UserWithRole2> getActiveUsersWithUserRole(UserRole userRole) {
		List<UserWithRole2> result = new ArrayList<>();

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		for (UserUserRoleAssignment assignment : userUserRoleAssignmentDao.findByUserRole(userRole)) {
			if (!assignment.getUser().isActive()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(assignment.getUser().getName());
			mapping.setUserUserId(assignment.getUser().getUserId());
			mapping.setUserUuid(assignment.getUser().getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromUserRoleAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);
		}
		
		for (PositionUserRoleAssignment assignment : positionUserRoleAssignmentDao.findByUserRole(userRole)) {
			if (!assignment.getPosition().getUser().isActive()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(assignment.getPosition().getUser().getName());
			mapping.setUserUserId(assignment.getPosition().getUser().getUserId());
			mapping.setUserUuid(assignment.getPosition().getUser().getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromPositionUserRoleAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);			
		}

		return result;
	}

	// TODO: we really should re-implement all the methods about roles into 2 methods
	// * getAllUsersWithRole()
	// * getAllRolesForUser()
	// and then optimize those, and use them everywhere else

	public List<UserWithRole> getUsersWithUserRole(UserRole userRole, boolean findIndirectlyAssignedRoles) {
		List<UserWithRole> result = new ArrayList<>();
		
		// this we ALWAYS need to do
		for (User user : userDao.findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, false)) {
			UserWithRole mapping = new UserWithRole();
			mapping.setUser(user);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}

		// get rolegroups that have the userrole included
		List<RoleGroup> roleGroups = roleGroupDao.findByUserRoleAssignmentsUserRole(userRole);

		// get users that have roleGroup assigned
		for (RoleGroup roleGroup : roleGroups) {
			for (User user : userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false)) {
				UserWithRole mapping = new UserWithRole();
				mapping.setUser(user);
				mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);

				result.add(mapping);
			}
		}

		if (findIndirectlyAssignedRoles) {

			// titles or position assignements (depending on configuration)
			if (!configuration.getTitles().isEnabled()) {
				// get positions that have userRole assigned
				for (Position position : positionService.getAllWithRole(userRole, false)) {
					if (position.getUser().isActive()) {
						UserWithRole mapping = new UserWithRole();
						mapping.setUser(position.getUser());
						mapping.setAssignedThrough(AssignedThrough.POSITION);
		
						result.add(mapping);
					}
				}
	
				// get positions that have roleGroup assigned
				for (RoleGroup roleGroup : roleGroups) {
					for (Position position : positionService.getAllWithRoleGroup(roleGroup, false)) {
						if (position.getUser().isActive()) {
							UserWithRole mapping = new UserWithRole();
							mapping.setUser(position.getUser());
							mapping.setAssignedThrough(AssignedThrough.POSITION);
		
							result.add(mapping);
						}
					}
				}
			}

			// get ous that have userRole assigned
			for (OrgUnit orgUnit : orgUnitService.getByUserRole(userRole, false)) {
				Optional<OrgUnitUserRoleAssignment> assignment = orgUnit.getUserRoleAssignments().stream().filter(ouura -> Objects.equals(ouura.getUserRole().getId(), userRole.getId())).findFirst();
				if (assignment.isEmpty()) {
					log.error("Could not find assignment (UserRoleId: " + userRole.getId() + ", OU: " + orgUnit.getUuid()+ ")");
					continue;
				}

				// Get list of excepted users (if any)
				ArrayList<String> exceptedUsersUuid = new ArrayList<>();
				OrgUnitUserRoleAssignment orgUnitUserRoleAssignment = assignment.get();

				// if titles are disabled skip
				if (!configuration.getTitles().isEnabled() && orgUnitUserRoleAssignment.isContainsTitles()) {
					continue;
				}
				
				if (orgUnitUserRoleAssignment.isContainsExceptedUsers()) {
					exceptedUsersUuid.addAll(orgUnitUserRoleAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
				}

				for (Position position : positionService.findByOrgUnit(orgUnit)) {
					if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
						continue;
					}
					
					if (orgUnitUserRoleAssignment.isContainsTitles() && !orgUnitUserRoleAssignment.getTitles().contains(position.getTitle())) {
						continue;
					}
					
					if (position.getUser().isActive() && !position.getUser().isDoNotInherit()) {
						UserWithRole mapping = new UserWithRole();
						mapping.setUser(position.getUser());

						if (orgUnitUserRoleAssignment.isContainsTitles()) {
							mapping.setAssignedThrough(AssignedThrough.TITLE);
						}
						else {
							mapping.setAssignedThrough(AssignedThrough.ORGUNIT);
						}
	
						result.add(mapping);
					}
				}

				// check if the assignment to the OrgUnit is flagged with inherit
				if (orgUnitUserRoleAssignment.isInherit()) {
					List<UserWithRole> inherited = getUserRoleMappingsRecursive(orgUnit.getChildren(), AssignedThrough.ORGUNIT);
					result.addAll(inherited);
				}
			}

			// get ous that have roleGroup assigned
			for (RoleGroup roleGroup : roleGroups) {
				for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {
					Optional<OrgUnitRoleGroupAssignment> assignment = orgUnit.getRoleGroupAssignments().stream().filter(ourga -> Objects.equals(ourga.getRoleGroup().getId(), roleGroup.getId())).findFirst();

					if (assignment.isEmpty()) {
						log.error("getUsersWithUserRole: Could not find assignment (RoleGroupId: " + roleGroup.getId() + ", OU: " + orgUnit.getUuid()+ ")");
						continue;
					}

					// Get list of excepted users (if any)
					ArrayList<String> exceptedUsersUuid = new ArrayList<>();
					OrgUnitRoleGroupAssignment orgUnitRoleGroupAssignment = assignment.get();
					if (orgUnitRoleGroupAssignment.isContainsExceptedUsers()) {
						exceptedUsersUuid.addAll(orgUnitRoleGroupAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
					}

					for (Position position : positionService.findByOrgUnit(orgUnit)) {
						if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
							continue;
						}

						if (orgUnitRoleGroupAssignment.isContainsTitles() && !orgUnitRoleGroupAssignment.getTitles().contains(position.getTitle())) {
							continue;
						}
						
						if (position.getUser().isActive() && !position.getUser().isDoNotInherit()) {
							UserWithRole mapping = new UserWithRole();
							mapping.setUser(position.getUser());
							
							if (orgUnitRoleGroupAssignment.isContainsTitles()) {
								mapping.setAssignedThrough(AssignedThrough.TITLE);
							}
							else {
								mapping.setAssignedThrough(AssignedThrough.ORGUNIT);
							}
	
							result.add(mapping);
						}
					}

					// check if the assignment to the OrgUnit is flagged with inherit
					if (orgUnitRoleGroupAssignment.isInherit()) {
						List<UserWithRole> inherited = getUserRoleMappingsRecursive(orgUnit.getChildren(), AssignedThrough.ORGUNIT);
						result.addAll(inherited);
					}
				}
			}
		}

		return result;
	}

	
	public List<UserAssignedToUserRoleDTO> getUserAssignmentsWithUserRoleDirectlyAssigned(UserRole userRole) {
		List<UserAssignedToUserRoleDTO> result = new ArrayList<>();
		
		// this we ALWAYS need to do
		@SuppressWarnings("deprecation") // ok, it is for UI
		List<User> userRoleUsers = userDao.findByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
		for (User user : userRoleUsers.stream().distinct().collect(Collectors.toList())) {
			List<UserUserRoleAssignment> userRoleAssignments = user.getUserRoleAssignments().stream().filter(a -> a.getUserRole().equals(userRole)).collect(Collectors.toList());
			for (UserUserRoleAssignment userUserRoleAssignment : userRoleAssignments) {
				UserAssignedToUserRoleDTO mapping = new UserAssignedToUserRoleDTO();
				mapping.setUser(user);
				mapping.setAssignedThrough(AssignedThrough.DIRECT);
				mapping.setAssignmentId(userUserRoleAssignment.getId());
				mapping.setCanEdit(false);
				mapping.setStartDate(userUserRoleAssignment.getStartDate());
				mapping.setStopDate(userUserRoleAssignment.getStopDate());
				
				result.add(mapping);
			}
		}

		// get rolegroups that have the userrole included
		List<RoleGroup> roleGroups = roleGroupDao.findByUserRoleAssignmentsUserRole(userRole);

		// get users that have roleGroup assigned
		for (RoleGroup roleGroup : roleGroups) {
			@SuppressWarnings("deprecation") // ok, it is for UI
			List<User> roleGroupUsers = userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
			for (User user : roleGroupUsers.stream().distinct().collect(Collectors.toList())) {
				List<UserRoleGroupAssignment> roleGroupAssignments = user.getRoleGroupAssignments().stream().filter(a -> a.getRoleGroup().equals(roleGroup)).collect(Collectors.toList());
				for (UserRoleGroupAssignment userRoleGroupAssignment : roleGroupAssignments) {
					UserAssignedToUserRoleDTO mapping = new UserAssignedToUserRoleDTO();
					mapping.setUser(user);
					mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);
					mapping.setAssignmentId(userRoleGroupAssignment.getId());
					mapping.setCanEdit(false);
					mapping.setStartDate(userRoleGroupAssignment.getStartDate());
					mapping.setStopDate(userRoleGroupAssignment.getStopDate());
					
					result.add(mapping);
				}
			}
		}
		
		return result;
	}

	public List<UserAssignedToRoleGroupDTO> getUserAssignmentsWithRoleGroupDirectlyAssigned(RoleGroup roleGroup) {
		List<UserAssignedToRoleGroupDTO> result = new ArrayList<>();
		
		// this we ALWAYS need to do
		@SuppressWarnings("deprecation") // ok, it is for UI
		List<User> userRoleUsers = userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
		for (User user : userRoleUsers.stream().distinct().collect(Collectors.toList())) {
			List<UserRoleGroupAssignment> roleGroupAssignments = user.getRoleGroupAssignments().stream().filter(a -> a.getRoleGroup().equals(roleGroup)).collect(Collectors.toList());
			for (UserRoleGroupAssignment roleGroupAssignment : roleGroupAssignments) {
				UserAssignedToRoleGroupDTO mapping = new UserAssignedToRoleGroupDTO();
				mapping.setUser(user);
				mapping.setAssignedThrough(AssignedThrough.DIRECT);
				mapping.setAssignmentId(roleGroupAssignment.getId());
				mapping.setCanEdit(false);
				mapping.setStartDate(roleGroupAssignment.getStartDate());
				mapping.setStopDate(roleGroupAssignment.getStopDate());
				
				result.add(mapping);
			}
		}

		return result;
	}
	
	public List<UserWithRoleAndDates> getUsersWithUserRoleDirectlyAssigned(UserRole userRole) {
		List<UserWithRoleAndDates> result = new ArrayList<>();
		
		// this we ALWAYS need to do
		@SuppressWarnings("deprecation") // ok, it is for UI
		List<User> userRoleUsers = userDao.findByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
		for (User user : userRoleUsers) {
			UserWithRoleAndDates mapping = new UserWithRoleAndDates();
			mapping.setUser(user);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);
			//There should only be one
			UserUserRoleAssignment userRoleAssignment = user.getUserRoleAssignments().stream().filter(a -> a.getUserRole().equals(userRole)).findFirst().orElse(null);
			if (userRoleAssignment != null) {
				mapping.setStartDate(userRoleAssignment.getStartDate());
				mapping.setStopDate(userRoleAssignment.getStopDate());
			}
			result.add(mapping);
		}

		// get rolegroups that have the userrole included
		List<RoleGroup> roleGroups = roleGroupDao.findByUserRoleAssignmentsUserRole(userRole);

		// get users that have roleGroup assigned
		for (RoleGroup roleGroup : roleGroups) {
			@SuppressWarnings("deprecation") // ok, it is for UI
			List<User> roleGroupUsers = userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
			for (User user : roleGroupUsers) {
				UserWithRoleAndDates mapping = new UserWithRoleAndDates();
				mapping.setUser(user);
				mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);
				UserRoleGroupAssignment userRoleGroupAssignment = user.getRoleGroupAssignments().stream().filter(a -> a.getRoleGroup().equals(roleGroup)).findFirst().orElse(null);
				if (userRoleGroupAssignment != null) {
					mapping.setStartDate(userRoleGroupAssignment.getStartDate());
					mapping.setStopDate(userRoleGroupAssignment.getStopDate());
				}
				result.add(mapping);
			}
		}
		return result;
	}
	
	public List<UserWithRoleAndDates> getUsersWithRoleGroupDirectlyAssigned(RoleGroup roleGroup) {
		List<UserWithRoleAndDates> result = new ArrayList<>();
		
		// this we ALWAYS need to do
		@SuppressWarnings("deprecation") // ok, it is for UI
		List<User> userRoleUsers = userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
		for (User user : userRoleUsers) {
			UserWithRoleAndDates mapping = new UserWithRoleAndDates();
			mapping.setUser(user);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);
			//There should only be one
			UserRoleGroupAssignment roleGroupAssignment = user.getRoleGroupAssignments().stream().filter(a -> a.getRoleGroup().equals(roleGroup)).findFirst().orElse(null);
			if (roleGroupAssignment != null) {
				mapping.setStartDate(roleGroupAssignment.getStartDate());
				mapping.setStopDate(roleGroupAssignment.getStopDate());
			}
			result.add(mapping);
		}
		return result;
	}

	private List<UserWithRole> getUserRoleMappingsRecursive(List<OrgUnit> children, AssignedThrough assignedThrough) {
		List<UserWithRole> result = new ArrayList<>();
		
		if (children != null) {	
			for (OrgUnit child : children) {
				if (!child.isActive()) {
					continue;
				}
	
				for (Position position : positionService.findByOrgUnit(child)) {
					if (position.getUser().isActive() && !position.getUser().isDoNotInherit()) {
						UserWithRole mapping = new UserWithRole();
						mapping.setUser(position.getUser());
						mapping.setAssignedThrough(assignedThrough);

						result.add(mapping);
					}
				}

				List<UserWithRole> inherited = getUserRoleMappingsRecursive(child.getChildren(), assignedThrough);
				result.addAll(inherited);
			}
		}
		
		return result;
	}

	@Deprecated
	public List<UserWithRole> getUsersWithRoleGroup(RoleGroup roleGroup, boolean findIndirectlyAssignedRoleGroups) {
		List<UserWithRole> result = new ArrayList<>();

		// get users that have roleGroup assigned
		for (User user : userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false)) {
			UserWithRole mapping = new UserWithRole();
			mapping.setUser(user);
			mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);

			result.add(mapping);
		}

		if (findIndirectlyAssignedRoleGroups) {
			if (!configuration.getTitles().isEnabled()) {
				// get positions that have roleGroup assigned
				for (Position position : positionService.getAllWithRoleGroup(roleGroup, false)) {
					if (position.getUser().isActive()) {
						UserWithRole mapping = new UserWithRole();
						mapping.setUser(position.getUser());
						mapping.setAssignedThrough(AssignedThrough.POSITION);
		
						result.add(mapping);
					}
				}
			}

			// get ous that have roleGroup assigned
			for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {
				List<OrgUnitRoleGroupAssignment> roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(ourga -> Objects.equals(ourga.getRoleGroup().getId(), roleGroup.getId())).collect(Collectors.toList());
				if (roleGroupAssignments.isEmpty()) {
					log.error("getUsersWithRoleGroup: Could not find assignment (RoleGroupId: " + roleGroup.getId() + ", OU: " + orgUnit.getUuid()+ ")");
					continue;
				}

				for (OrgUnitRoleGroupAssignment orgUnitRoleGroupAssignment : roleGroupAssignments) {
					// Get list of excepted users (if any)
					ArrayList<String> exceptedUsersUuid = new ArrayList<>();
					if (orgUnitRoleGroupAssignment.isContainsExceptedUsers()) {
						exceptedUsersUuid.addAll(orgUnitRoleGroupAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
					}

					ArrayList<String> titles = new ArrayList<>();
					if (configuration.getTitles().isEnabled() && orgUnitRoleGroupAssignment.isContainsTitles()) {
						titles.addAll(orgUnitRoleGroupAssignment.getTitles().stream().map(Title::getUuid).collect(Collectors.toList()));
					}
	
					for (Position position : positionService.findByOrgUnit(orgUnit)) {
						// exceptedUsers, and user is mentioned, skip
						if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
							continue;
						}

						// titles enabled and title assignment, but not title-match, skip
						if (configuration.getTitles().isEnabled() && orgUnitRoleGroupAssignment.isContainsTitles()) {
							if (position.getTitle() == null || !titles.contains(position.getTitle().getUuid())) {
								continue;
							}
						}
						
						if (position.getUser().isActive() && !position.getUser().isDoNotInherit()) {
							UserWithRole mapping = new UserWithRole();
							mapping.setUser(position.getUser());
							mapping.setAssignedThrough(AssignedThrough.ORGUNIT);
		
							result.add(mapping);
						}
					}

					// check if the assignment to the OrgUnit is flagged with inherit
					if (orgUnitRoleGroupAssignment.isInherit()) {
						List<UserWithRole> inherited = getUserRoleMappingsRecursive(orgUnit.getChildren(), AssignedThrough.ORGUNIT);
						result.addAll(inherited);
					}
				}
			}
		}

		return result;
	}

	public String generateOIOBPP(User user, List<ItSystem> itSystems, Map<String, String> roleMap) throws UserNotFoundException {
		// santity check - this happens a bit to often
		if (StringUtils.isEmpty(configuration.getIntegrations().getKombit().getDomain())) {
			throw new RuntimeException("Badly configured - no domain available");
		}

		List<PrivilegeGroup> privilegeGroups = generateOIOBPPPrivileges(user, itSystems, roleMap);

		StringBuilder builder = new StringBuilder();
		builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		builder.append("<bpp:PrivilegeList xmlns:bpp=\"http://itst.dk/oiosaml/basic_privilege_profile\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");

		for (PrivilegeGroup privilegeGroup : privilegeGroups) {
			builder.append("<PrivilegeGroup Scope=\"urn:dk:gov:saml:cvrNumberIdentifier:" + privilegeGroup.getCvr() + "\">");
			builder.append("<Privilege>" + privilegeGroup.getPrivilege().getIdentifier() + "</Privilege>");					

			for (Constraint constraint : privilegeGroup.getConstraints()) {
				if (constraint.getParameter() != null && constraint.getParameter().length() > 0) {
					builder.append("<Constraint Name=\"" + constraint.getParameter() + "\">" + constraint.getValue() + "</Constraint>");
				}
			}
			builder.append("</PrivilegeGroup>");
		}
		builder.append("</bpp:PrivilegeList>");

		return Base64.getEncoder().encodeToString(builder.toString().getBytes());
	}
	
	public List<PrivilegeGroup> generateOIOBPPPrivileges(User user, List<ItSystem> itSystems, Map<String, String> roleMap) {
		List<PrivilegeGroup> result = new ArrayList<>();

		if (itSystems == null) {
			itSystems = itSystemService.getAll();
		}

		//filter out itsystems that are blocked
		itSystems = itSystems.stream().filter(its -> its.isAccessBlocked() == false).collect(Collectors.toList());

		List<UserRoleAssignmentWithInfo> userRoles = getAllUserRolesAssignmentsWithInfo(user, itSystems);
		
		// should we create a PrivilegeGroup per systemrole or per userrole?
		boolean expandToBsr = shouldExpandToBsr(itSystems);
		
		PrivilegeGroup privilegeGroup = new PrivilegeGroup(); // dummy assignment to make IDE happy
		for (UserRoleAssignmentWithInfo userRoleWithPostponedConstraints : userRoles) {
			UserRole userRole = userRoleWithPostponedConstraints.getUserRole();
			
			// delegated roles cannot be constrained dynamically, nor can it be expanded to BSRs
			if (userRole.getDelegatedFromCvr() != null) {
				if (!expandToBsr) {
					roleMap.put(userRole.getIdentifier(), userRole.getName() + " (" + userRole.getItSystem().getName() + ")");
	
					privilegeGroup = new PrivilegeGroup();
					privilegeGroup.setCvr(userRole.getDelegatedFromCvr());
					privilegeGroup.setPrivilege(Privilege.builder()
							.identifier(userRole.getIdentifier())
							.name(userRole.getName())
							.build());
					
					result.add(privilegeGroup);
					privilegeGroup = null;
				}

				continue;
			}
			else {
				if (!expandToBsr) {
					roleMap.put(IdentifierGenerator.buildKombitIdentifier(userRole.getIdentifier(), configuration.getIntegrations().getKombit().getDomain()), userRole.getName() + " (" + userRole.getItSystem().getName() + ")");

					privilegeGroup = new PrivilegeGroup();
					privilegeGroup.setCvr(configuration.getCustomer().getCvr());
					privilegeGroup.setPrivilege(Privilege.builder()
							.identifier(IdentifierGenerator.buildKombitIdentifier(userRole.getIdentifier(), configuration.getIntegrations().getKombit().getDomain()))
							.name(userRole.getName())
							.build());
				}

				for (SystemRoleAssignment systemRole : userRole.getSystemRoleAssignments()) {
					if (expandToBsr) {
						roleMap.put(userRole.getIdentifier(), userRole.getName() + " (" + userRole.getItSystem().getName() + ")");

						privilegeGroup = new PrivilegeGroup();
						privilegeGroup.setCvr(configuration.getCustomer().getCvr());
						Privilege privilege = Privilege.builder()
								.identifier(systemRole.getSystemRole().getIdentifier())
								.name(systemRole.getSystemRole().getName())
								.build();
						
						privilegeGroup.setPrivilege(privilege);							
					}

					for (SystemRoleAssignmentConstraintValue constraint : systemRole.getConstraintValues()) {
						StringBuilder constraintValue = new StringBuilder();

						switch (constraint.getConstraintValueType()) {
							case INHERITED:
								if (constraint.getConstraintType().getEntityId().equals(Constants.KLE_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getKLEConstraint(user, ConstraintValueType.INHERITED));
								}
								else if (constraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getOrganisationConstraint(user, false));
								}
								break;
							case EXTENDED_INHERITED:
								if (constraint.getConstraintType().getEntityId().equals(Constants.KLE_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getKLEConstraint(user, ConstraintValueType.EXTENDED_INHERITED));
								}
								else if (constraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getOrganisationConstraint(user, true));
								}
								break;
							case READ_AND_WRITE:
								if (constraint.getConstraintType().getEntityId().equals(Constants.KLE_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getKLEConstraint(user, ConstraintValueType.READ_AND_WRITE));
								}
								break;
							case LEVEL_1:
							case LEVEL_2:
							case LEVEL_3:
							case LEVEL_4:
								if (constraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getOrganisationConstraint(user, constraint.getConstraintValueType()));
								}
								break;
							case VALUE:
								constraintValue.append(constraint.getConstraintValue());
								break;
							case POSTPONED:
								if (userRoleWithPostponedConstraints.getPostponedConstraints() != null) {
									for (PostponedConstraint postponedConstraint : userRoleWithPostponedConstraints.getPostponedConstraints()) {
										if (postponedConstraint.getConstraintType().getId() == constraint.getConstraintType().getId()) {
											constraintValue.append(postponedConstraint.getValue());
										}
									}
								}
								break;
						}

						if (constraintValue.length() == 0) {
							log.warn("User '" + user.getUserId() + "' has a userrole '" + userRole.getIdentifier()
									+ "' with a systemrole '" + systemRole.getSystemRole().getIdentifier()
									+ "' with a broken constraint of type '" + constraint.getConstraintType().getId() + "'");
							continue;
						}

						privilegeGroup.getConstraints().add(Constraint.builder()
								.parameter((expandToBsr) ? constraint.getConstraintType().getEntityId() : constraint.getConstraintIdentifier())
								.type(constraint.getConstraintType().getEntityId())
								.value(constraintValue.toString())
								.build());
					}
					
					if (expandToBsr) {
						result.add(privilegeGroup);
						privilegeGroup = null;
					}
				}
			}
			
			if (!expandToBsr && privilegeGroup != null) {
				result.add(privilegeGroup);
			}
		}

		return result;
	}

	private String getKLEConstraint(User user, ConstraintValueType constraintValueType) {
		Set<String> kleSet = new HashSet<>();

		switch (constraintValueType) {
			case POSTPONED:
				throw new RuntimeException("POSTPONED constraints should never call getKLEConstraint method");
			case INHERITED:
				kleSet.addAll(kleService.getKleAssignments(user, KleType.PERFORMING, true).stream().map(k -> k.getCode()).collect(Collectors.toList()));
				break;
			case EXTENDED_INHERITED:
				kleSet.addAll(kleService.getKleAssignments(user, KleType.INTEREST, true).stream().map(k -> k.getCode()).collect(Collectors.toList()));
				break;
			case READ_AND_WRITE:
				List<String> performings = kleService.getKleAssignments(user, KleType.PERFORMING, true).stream().map(k -> k.getCode()).collect(Collectors.toList());
				List<String> interests = kleService.getKleAssignments(user, KleType.INTEREST, true).stream().map(k -> k.getCode()).collect(Collectors.toList());
	
				Set<String> tempSet = new HashSet<>();
				tempSet.addAll(performings);
				tempSet.addAll(interests);

				// add all main groups - filtering out duplicates with Set<String> functionality
				for (String str : tempSet) {
					if (str.length() == 2) {
						kleSet.add(str);
					}
				}

				// add all groups - filtering out duplicates with startsWith() check
				for (String str : tempSet) {
					if (str.length() == 5) {
						boolean toAdd = true;
	
						for (String kle : kleSet) {
							if (str.startsWith(kle)) {
								toAdd = false;
							}
						}
	
						if (toAdd) {
							kleSet.add(str);
						}
					}
				}
	
				// add all subjects - filtering out duplicates with startsWith() check
				for (String str : tempSet) {
					if (str.length() == 8) {
						boolean toAdd = true;
	
						for (String kle : kleSet) {
							if (str.startsWith(kle)) {
								toAdd = false;
							}
						}
	
						if (toAdd) {
							kleSet.add(str);
						}
					}
				}
	
				break;
			case LEVEL_1:
			case LEVEL_2:
			case LEVEL_3:
			case LEVEL_4:
			case VALUE: // do nothing, data has been provisioned already
				break;
		}

		// generate KLE string
		StringBuilder builder = new StringBuilder();
		for (String code : kleSet) {
			if (builder.length() > 0) {
				builder.append(",");
			}

			builder.append(code);
			if (code.length() < 8) {
				builder.append(".*");
			}
		}

		// special case - we are looking for KLE, but found none, which will cause the
		// login to fail for that user. So instead we supply the magic value 99.99.99,
		// which should allow the login to work, but without giving access to any real
		// data for that user (at least for this role)
		if (builder.length() == 0) {
			builder.append("99.99.99");
		}

		return builder.toString();
	}

	private static String getOrganisationConstraint(User user, ConstraintValueType constraintValueType) {
		Set<String> result = new HashSet<>();
		
		for (Position position : user.getPositions()) {
			OrgUnit orgUnitWithRequiredLevel = null;
			
			OrgUnit ou = position.getOrgUnit();
			do {
				switch (ou.getLevel()) {
					case LEVEL_1:
						orgUnitWithRequiredLevel = ou;
						break;
					case LEVEL_2:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_2) ||
							constraintValueType.equals(ConstraintValueType.LEVEL_3) ||
							constraintValueType.equals(ConstraintValueType.LEVEL_4)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case LEVEL_3:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_3) ||
							constraintValueType.equals(ConstraintValueType.LEVEL_4)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case LEVEL_4:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_4)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case NONE:
						break;
				}

				if (orgUnitWithRequiredLevel != null) {
					break;
				}

				ou = ou.getParent();
			} while (ou != null);
			
			if (orgUnitWithRequiredLevel != null) {
				appendThisAndChildren(orgUnitWithRequiredLevel, result);
			}
			else {
				result.add(position.getOrgUnit().getUuid());
			}
		}

		return String.join(",", result);
	}

	private static String getOrganisationConstraint(User user, boolean extended) {
		Set<String> result = new HashSet<>();

		for (Position position : user.getPositions()) {
			OrgUnit orgUnit = position.getOrgUnit();

			if (extended) {
				appendThisAndChildren(orgUnit, result);
			}
			else {
				result.add(orgUnit.getUuid());
			}
		}

		return String.join(",", result);
	}

	private static void appendThisAndChildren(OrgUnit orgUnit, Set<String> result) {
		result.add(orgUnit.getUuid());

		appendChildren(orgUnit, result);
	}

	private static void appendChildren(OrgUnit orgUnit, Set<String> result) {
		for (OrgUnit child : orgUnit.getChildren()) {
			if (!child.isActive()) {
				continue;
			}

			result.add(child.getUuid());

			appendChildren(child, result);
		}
	}
	
	public List<User> getAllThin() {
		List<User> usersFlat = jdbcTemplate.query(SELECT_THIN_USERS_SQL, (RowMapper<User>) (rs, rowNum) -> {
			User user = new User();
			user.setUuid(rs.getString("uuid"));
			user.setUserId(rs.getString("userid"));
			user.setName(rs.getString("username"));
			user.setPositions(new ArrayList<>());

			String title = rs.getString("title");
			String ouName = rs.getString("orgunitName");
			String ouUuid = rs.getString("orgunitUuid");
			if (title != null && ouName != null) {
				OrgUnit orgUnit = new OrgUnit();
				orgUnit.setName(ouName);
				orgUnit.setUuid(ouUuid);

				Position position = new Position();
				position.setName(title);
				position.setOrgUnit(orgUnit);
				
				user.getPositions().add(position);
			}

			return user;
		});
		
		List<User> result = new ArrayList<>();
		User[] usersFlatArray = usersFlat.toArray(new User[0]);

		for (int i = 0; i < usersFlatArray.length; i++) {
			User user = usersFlatArray[i];

			boolean addToResult = true;

			// if the user exists further into the array, move position object, and skip onwards
			for (int j = i+1; j < usersFlatArray.length; j++) {
				if (usersFlatArray[j].getUuid().equals(user.getUuid())) {
					if (user.getPositions().size() > 0) {
						usersFlatArray[j].getPositions().addAll(user.getPositions());
					}

					addToResult = false;
					break;
				}
			}

			if (addToResult) {
				result.add(user);
			}
		}
		
		return result;
	}

	private boolean shouldExpandToBsr(List<ItSystem> itSystems) {
		for (ItSystem itSystem : itSystems) {
			if (!itSystem.getSystemType().equals(ItSystemType.KOMBIT)) {
				return true;
			}
		}

		return false;
	}

	public List<User> findByOrgUnit(OrgUnit ou) {
		List<Position> positions = positionService.findByOrgUnit(ou);
		Set<User> users = new HashSet<>();

		for (Position position : positions) {
			if (position.getUser().isActive()) {
				users.add(position.getUser());
			}
		}

		return new ArrayList<>(users);
	}

	public List<User> getManager(User user) {
		List<User> managers = new ArrayList<>();

		// get all it-systems from all orgunits that the user resides in
		Set<OrgUnit> orgUnits = user.getPositions().stream()
				.map(p -> p.getOrgUnit())
				.collect(Collectors.toSet());

		for (OrgUnit orgUnit : orgUnits) {
			if (orgUnit.getManager() != null) {
				User manager = orgUnit.getManager();
				
				managers.add(manager);
			}
		}

		return managers;
	}

	// will return false for substitutes, so take care to use this when a MANAGER is needed
	public boolean isManager(User user) {
		List<OrgUnit> orgUnits = orgUnitService.getByManagerMatchingUser(user);
		
		return (orgUnits != null && orgUnits.size() > 0);
	}
	
	public boolean isManagerFor(User user) {
		if (!SecurityUtil.hasRole(Constants.ROLE_MANAGER) && !SecurityUtil.hasRole(Constants.ROLE_SUBSTITUTE)) {
			return false;
		}
		
		// see if the currently logged in user is manager for an OU where the user has a position
		List<OrgUnit> orgUnits = orgUnitService.getByManager();
		for (OrgUnit orgUnit : orgUnits) {
			for (Position position : user.getPositions()) {
				if (position.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
					return true;
				}
			}
		}
		
		return false;
	}

	public User getLatestUpdatedUser() {
		return userDao.getTopByActiveTrueOrderByLastUpdatedDesc();
	}
	
	public void removeAllDirectlyAssignedRolesAndInformUser(User user) {
		if (user.getUserRoleAssignments().size() == 0 && user.getRoleGroupAssignments().size() == 0) {
			return;
		}

		StringBuilder builder = new StringBuilder();

		builder.append("Til " + user.getName() + "<br/><br/>");
		builder.append("I forbindelse med en organisatorisk opdatering er følgende rettigheder blevet fjernet:<br/><ul>");
		
		removeAllDirectlyAssignedRoles(user, builder);
		
		builder.append("</ul>");
		
		if (user.getEmail() != null && user.getEmail().length() > 0) {
			emailService.sendMessage(user.getEmail(), "Ændringer i dine rettigheder", builder.toString());
		}
	}

	private void removeAllDirectlyAssignedRoles(User user, StringBuilder builder) {
		while (user.getUserRoleAssignments().size() > 0) {
			UserRole role = user.getUserRoleAssignments().get(0).getUserRole();
			removeUserRole(user, role);

			builder.append("<li>Jobfunktionsrollen " + role.getName() + " i " + role.getItSystem().getName() + "</li>");
			
			log.info("Removing userRole '" + role.getItSystem().getName() + "/" + role.getName() + " (" + role.getId() + ")' from user '" + user.getUserId() + "'");
		}
		
		while (user.getUserRoleAssignments().size() > 0) {
			RoleGroup role = user.getRoleGroupAssignments().get(0).getRoleGroup();
			removeRoleGroup(user, role);

			builder.append("<li>Rollebuketten " + role.getName() + "</li>");

			log.info("Removing roleGroup '" + role.getName() + " (" + role.getId() + ")' from user '" + user.getUserId() + "'");
		}
	}

	public void removeAllDirectAndOrgUnitAssignedRolesAndInformUser(User user, OrgUnit orgUnit) {
		// get all relevant positions
		List<Position> positions = new ArrayList<>();
		for (Position position : user.getPositions()) {
			if (position.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
				positions.add(position);
			}
		}
		
		if (positions.size() == 0 && user.getUserRoleAssignments().size() == 0 && user.getRoleGroupAssignments().size() == 0) {
			return;
		}

		StringBuilder builder = new StringBuilder();

		builder.append("Til " + user.getName() + "<br/><br/>");
		builder.append("I forbindelse med en organisatorisk opdatering er følgende rettigheder blevet fjernet:<br/><ul>");

		removeAllDirectlyAssignedRoles(user, builder);
		
		for (Position position : positions) {
			while (position.getUserRoleAssignments().size() > 0) {
				UserRole role = position.getUserRoleAssignments().get(0).getUserRole();
				positionService.removeUserRole(position, role);

				builder.append("<li>Jobfunktionsrollen " + role.getName() + " i " + role.getItSystem().getName() + "</li>");
				
				log.info("Removing userRole '" + role.getItSystem().getName() + "/" + role.getName() + " (" + role.getId() + ")' from user '" + user.getUserId() + "'");
			}
			
			while (position.getRoleGroupAssignments().size() > 0) {
				RoleGroup role = position.getRoleGroupAssignments().get(0).getRoleGroup();
				positionService.removeRoleGroup(position, role);

				builder.append("<li>Rollebuketten " + role.getName() + "</li>");

				log.info("Removing roleGroup '" + role.getName() + " (" + role.getId() + ")' from user '" + user.getUserId() + "'");
			}
		}
		
		builder.append("</ul>");
		
		if (user.getEmail() != null && user.getEmail().length() > 0) {
			emailService.sendMessage(user.getEmail(), "Ændringer i dine rettigheder", builder.toString());
		}
	}

	public List<User> getSubstitutesManager(User user) {
		return userDao.getByManagerSubstitute(user);
	}

	@Transactional(rollbackFor = Exception.class)
	public void removeOldInactiveUsers() {
		List<User> toBeDeleted = new ArrayList<User>();
		List<User> listOfInactiveUsers = getAllInactive();

		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.MONTH, -6);
		Date sixMonthsFromNow = cal.getTime();

		for (User user : listOfInactiveUsers) {
			if (user.getLastUpdated() == null || user.getLastUpdated().before(sixMonthsFromNow)) {
				log.info("Deleting user object '" + user.getUserId() + "' because of inactive state for a long period of time");
				toBeDeleted.add(user);
			}
		}

		if (toBeDeleted.size() > 0) {
			userDao.deleteAll(toBeDeleted);
		}
	}
}
