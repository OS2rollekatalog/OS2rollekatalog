package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleNotAssignedDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.Constraint;
import dk.digitalidentity.rc.service.model.Privilege;
import dk.digitalidentity.rc.service.model.PrivilegeGroup;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import dk.digitalidentity.rc.service.model.UserWithRole;
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
	private TitleService titleService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EmailService emailService;
	
	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
		
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
	
	@Deprecated
	public List<User> getByRoleGroupsIncludingInactive(RoleGroup role) {
		return userDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroup(role);
	}

	@Deprecated
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
	public boolean addRoleGroup(User user, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		if (!user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
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

			return true;
		}

		return false;
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
	public boolean addUserRole(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan tildele Rollekatalog roller");
		}

		if (!user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			UserUserRoleAssignment assignment = new UserUserRoleAssignment();
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
			assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			assignment.setStopDate(stopDate);
			assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
			user.getUserRoleAssignments().add(assignment);

			return true;
		}

		return false;
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

		// user.getUserRoles()
		List<UserRole> userRoles = user.getUserRoleAssignments().stream()
				.filter(ura -> !ura.isInactive())
				.map(ura -> ura.getUserRole())
				.collect(Collectors.toList());

		for (UserRole role : userRoles) {
			if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
				UserRoleAssignedToUser assignment = new UserRoleAssignedToUser();
				assignment.setUserRole(role);
				assignment.setAssignedThrough(AssignedThrough.DIRECT);

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
				List<UserRole> ur = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
	
				for (UserRole role : ur) {
					if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignedToUser assignment = new UserRoleAssignedToUser();
						assignment.setUserRole(role);
						assignment.setAssignedThrough(AssignedThrough.ROLEGROUP);
	
						result.add(assignment);
					}
				}
			}
		}

		// user.getPositions() -> everything assigned through these
		for (Position position : user.getPositions()) {

			// position.getRoles();
			List<UserRole> pur = position.getUserRoleAssignments().stream()
					.filter(ura -> !ura.isInactive())
					.map(ura -> ura.getUserRole())
					.collect(Collectors.toList());

			for (UserRole role : pur) {
				if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
					UserRoleAssignedToUser assignment = new UserRoleAssignedToUser();
					assignment.setUserRole(role);
					assignment.setAssignedThrough(AssignedThrough.POSITION);

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
					List<UserRole> rur = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
	
					for (UserRole role : rur) {
						if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
							UserRoleAssignedToUser assignment = new UserRoleAssignedToUser();
							assignment.setUserRole(role);
							assignment.setAssignedThrough(AssignedThrough.POSITION);
	
							result.add(assignment);
						}
					}
				}
			}
			
			if (!user.isDoNotInherit() && position.getTitle() != null) {

				// position.title (userRoles)
				for (TitleUserRoleAssignment assignment : position.getTitle().getUserRoleAssignments()) {
					if (assignment.isInactive()) {
						continue;
					}

					// if the assignment is restricted to specific OUs, check if this position's OU is included
					if (assignment.getOuUuids().size() > 0 && !assignment.getOuUuids().contains(position.getOrgUnit().getUuid())) {
						continue;
					}

					if (itSystem == null || assignment.getUserRole().getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignedToUser uratu = new UserRoleAssignedToUser();
						uratu.setUserRole(assignment.getUserRole());
						uratu.setAssignedThrough(AssignedThrough.TITLE);
						uratu.setTitle(position.getTitle());
						
						result.add(uratu);
					}
				}

				// position.title (RoleGroups)
				if (expandRoleGroups) {
					for (TitleRoleGroupAssignment assignment : position.getTitle().getRoleGroupAssignments()) {
						if (assignment.isInactive()) {
							continue;
						}
	
						// if the assignment is restricted to specific OUs, check if this position's OU is included
						if (assignment.getOuUuids().size() > 0 && !assignment.getOuUuids().contains(position.getOrgUnit().getUuid())) {
							continue;
						}
	
						List<UserRole> rur = assignment.getRoleGroup().getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
	
						for (UserRole role : rur) {
							if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
								UserRoleAssignedToUser uratu = new UserRoleAssignedToUser();
								uratu.setUserRole(role);
								uratu.setAssignedThrough(AssignedThrough.TITLE);
								uratu.setTitle(position.getTitle());
								
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
		List<RoleGroupAssignedToUser> result = new ArrayList<>();

		// user.getRoleGroups()
		List<RoleGroup> roleGroups = user.getRoleGroupAssignments().stream()
				.filter(ura -> !ura.isInactive())
				.map(ura -> ura.getRoleGroup())
				.collect(Collectors.toList());

		for (RoleGroup roleGroup : roleGroups) {
			RoleGroupAssignedToUser assignment = new RoleGroupAssignedToUser();
			assignment.setRoleGroup(roleGroup);
			assignment.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(assignment);
		}

		// user.getPositions() -> everything assigned through these
		for (Position position : user.getPositions()) {

			// position.getRoleGroups
	      	List<RoleGroup> prg = position.getRoleGroupAssignments().stream()
					.filter(ura -> !ura.isInactive())
	      			.map(ura -> ura.getRoleGroup())
	      			.collect(Collectors.toList());
	      	
			for (RoleGroup roleGroup : prg) {
				RoleGroupAssignedToUser assignment = new RoleGroupAssignedToUser();
				assignment.setRoleGroup(roleGroup);
				assignment.setAssignedThrough(AssignedThrough.POSITION);

				result.add(assignment);
			}

			if (!user.isDoNotInherit() && position.getTitle() != null) {

				// position.title (RoleGroups)
				for (TitleRoleGroupAssignment assignment : position.getTitle().getRoleGroupAssignments()) {
					if (assignment.isInactive()) {
						continue;
					}

					// if the assignment is restricted to specific OUs, check if this position's OU is included
					if (assignment.getOuUuids().size() > 0 && !assignment.getOuUuids().contains(position.getOrgUnit().getUuid())) {
						continue;
					}


					RoleGroupAssignedToUser rgatu = new RoleGroupAssignedToUser();
					rgatu.setRoleGroup(assignment.getRoleGroup());
					rgatu.setAssignedThrough(AssignedThrough.TITLE);
					rgatu.setTitle(position.getTitle());

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
	
	private void getAllRoleGroupsFromOrgUnit(List<RoleGroupAssignedToUser> result, OrgUnit orgUnit, boolean inheritOnly, User user) {

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
			
			RoleGroupAssignedToUser assignment = new RoleGroupAssignedToUser();
			assignment.setRoleGroup(roleGroupMapping.getRoleGroup());
			assignment.setAssignedThrough(AssignedThrough.ORGUNIT);
			assignment.setOrgUnit(orgUnit);

			result.add(assignment);
		}
		
		// recursive upwards in the hierarchy (only inherited roles)
		if (orgUnit.getParent() != null) {
			getAllRoleGroupsFromOrgUnit(result, orgUnit.getParent(), true, user);
		}
	}

	private void getAllUserRolesFromOrgUnit(List<UserRoleAssignedToUser> result, OrgUnit orgUnit, ItSystem itSystem, boolean inheritOnly, boolean expandRoleGroups, User user) {

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
			
			UserRole role = roleMapping.getUserRole();

			if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
				UserRoleAssignedToUser assignment = new UserRoleAssignedToUser();
				assignment.setUserRole(role);
				assignment.setAssignedThrough(AssignedThrough.ORGUNIT);
				assignment.setOrgUnit(orgUnit);
				
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

				RoleGroup roleGroup = roleGroupMapping.getRoleGroup();
				
				List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
				for (UserRole role : userRoles) {
					if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignedToUser assignment = new UserRoleAssignedToUser();
						assignment.setUserRole(role);
						assignment.setAssignedThrough(AssignedThrough.ORGUNIT);
						assignment.setOrgUnit(orgUnit);
	
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
			else {
				for (Title title : titleService.getAllWithRole(userRole)) {
					Optional<TitleUserRoleAssignment> assignment = title.getUserRoleAssignments().stream().filter(u -> u.getUserRole().getId() == userRole.getId()).findFirst();
					if (assignment.isPresent()) {
						TitleUserRoleAssignment tura = assignment.get();

						for (Position position : positionService.getAllWithTitle(title, false)) {
							if (tura.getOuUuids().contains(position.getOrgUnit().getUuid())) {
								UserWithRole mapping = new UserWithRole();
								mapping.setUser(position.getUser());
								mapping.setAssignedThrough(AssignedThrough.TITLE);
		
								result.add(mapping);
							}
						}
					}
				}
				
				for (RoleGroup roleGroup : roleGroups) {
					for (Title title : titleService.getAllWithRoleGroup(roleGroup)) {
						Optional<TitleRoleGroupAssignment> assignment = title.getRoleGroupAssignments().stream().filter(u -> u.getRoleGroup().getId() == roleGroup.getId()).findFirst();
						if (assignment.isPresent()) {
							TitleRoleGroupAssignment trga = assignment.get();

							for (Position position : positionService.getAllWithTitle(title, false)) {
								if (trga.getOuUuids().contains(position.getOrgUnit().getUuid())) {
									UserWithRole mapping = new UserWithRole();
									mapping.setUser(position.getUser());
									mapping.setAssignedThrough(AssignedThrough.TITLE);
			
									result.add(mapping);
								}
							}
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
				if (orgUnitUserRoleAssignment.isContainsExceptedUsers()) {
					exceptedUsersUuid.addAll(orgUnitUserRoleAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
				}

				for (Position position : positionService.findByOrgUnit(orgUnit)) {
					if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
						continue;
					}
					if (position.getUser().isActive() && !position.getUser().isDoNotInherit()) {
						UserWithRole mapping = new UserWithRole();
						mapping.setUser(position.getUser());
						mapping.setAssignedThrough(AssignedThrough.ORGUNIT);
	
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
						log.error("Could not find assignment (RoleGroupId: " + roleGroup.getId() + ", OU: " + orgUnit.getUuid()+ ")");
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
			else {
				for (Title title : titleService.getAllWithRoleGroup(roleGroup)) {
					Optional<TitleRoleGroupAssignment> assignment = title.getRoleGroupAssignments().stream().filter(u -> u.getRoleGroup().getId() == roleGroup.getId()).findFirst();
					if (assignment.isPresent()) {
						TitleRoleGroupAssignment trga = assignment.get();

						for (Position position : positionService.getAllWithTitle(title, false)) {
							if (trga.getOuUuids().contains(position.getOrgUnit().getUuid())) {
								UserWithRole mapping = new UserWithRole();
								mapping.setUser(position.getUser());
								mapping.setAssignedThrough(AssignedThrough.TITLE);
		
								result.add(mapping);
							}
						}
					}
				}
			}

			// get ous that have roleGroup assigned
			for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {
				Optional<OrgUnitRoleGroupAssignment> assignment = orgUnit.getRoleGroupAssignments().stream().filter(ourga -> Objects.equals(ourga.getRoleGroup().getId(), roleGroup.getId())).findFirst();
				if (assignment.isEmpty()) {
					log.error("Could not find assignment (RoleGroupId: " + roleGroup.getId() + ", OU: " + orgUnit.getUuid()+ ")");
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

		return result;
	}

	public String generateOIOBPP(User user, List<ItSystem> itSystems, Map<String, String> roleMap) throws UserNotFoundException {
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

		List<UserRole> userRoles = getAllUserRoles(user, itSystems);
		
		// should we create a PrivilegeGroup per systemrole or per userrole?
		boolean expandToBsr = shouldExpandToBsr(itSystems);
		
		PrivilegeGroup privilegeGroup = new PrivilegeGroup(); // dummy assignment to make IDE happy
		for (UserRole userRole : userRoles) {

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
								continue;
						}

						if (constraintValue.length() == 0) {
							log.warn("User '" + user.getUserId() + "' has a userrole '" + userRole.getIdentifier()
									+ "' with a systemrole '" + systemRole.getSystemRole().getIdentifier()
									+ "' with a broken constraint of type '" + constraint.getConstraintType().getId() + "'");
							continue;
						}

						privilegeGroup.getConstraints().add(Constraint.builder()
								.parameter(constraint.getConstraintIdentifier())
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
