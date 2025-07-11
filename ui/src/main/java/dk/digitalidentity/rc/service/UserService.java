package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Comparator;
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

import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.DatatablesUserDao;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleNotAssignedDTO;
import dk.digitalidentity.rc.dao.PositionRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.PositionUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RequestApprovePostponedConstraint;
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
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
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
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentWithInfo;
import dk.digitalidentity.rc.service.model.UserRoleWithAssignmentIdDTO;
import dk.digitalidentity.rc.service.model.UserRoleWithPostponedConstraintDTO;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.service.model.UserWithRole2;
import dk.digitalidentity.rc.service.model.UserWithRoleAndDates;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import dk.digitalidentity.rc.util.StreamExtensions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableCaching
public class UserService {
	private static final String SELECT_THIN_USERS_SQL = "SELECT u.uuid AS uuid, u.name AS username, user_id AS userid, p.name AS title, o.name AS orgunitName, o.uuid AS orgunitUuid FROM users u JOIN positions p ON p.user_uuid = u.uuid JOIN ous o ON o.uuid = p.ou_uuid WHERE u.deleted = 0";

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

	@Autowired
	private DomainService domainService;

	@Autowired
	private DatatablesUserDao datatablesUserDao;
	
	@Autowired
	private SettingsService settingsService;

	// dao methods

	public User save(User user) {
		return userDao.save(user);
	}

	public void save(List<User> list) {
		userDao.saveAll(list);
	}

	public void delete(User user) {
		userDao.delete(user);
	}

	public long countAllWithRoleGroup(RoleGroup role) {
		return userDao.countByDeletedFalseAndRoleGroupAssignmentsRoleGroup(role);
	}

	public long countAllWithRole(UserRole userRole) {
		return userDao.countByDeletedFalseAndUserRoleAssignmentsUserRole(userRole);
	}

	public List<User> getByExtUuid(String uuid) {
		return userDao.findByExtUuidAndDeletedFalse(uuid);
	}

	@Deprecated // Please use the version returning optional
	public User getByUuid(String uuid) {
		return userDao.findByUuidAndDeletedFalse(uuid).orElse(null);
	}

	public Optional<User> getOptionalByUuid(String uuid) {
		return userDao.findByUuidAndDeletedFalse(uuid);
	}

	public Optional<User> getOptionalByUuidIncludingDeleted(String uuid) {
		return userDao.findByUuid(uuid);
	}

	// using the default domain - used when getting user from SecurityUtil, Principal and simulating logins
	public User getByUserId(String userId) {
		return userDao.findByUserIdAndDomainAndDeletedFalse(userId, domainService.getPrimaryDomain()).orElse(null);
	}

	// using the default domain - used when getting user from SecurityUtil, Principal and simulating logins
	public Optional<User> getOptionalByUserId(String userId) {
		return userDao.findByUserIdAndDomainAndDeletedFalse(userId, domainService.getPrimaryDomain());
	}

	public User getByUserId(String userId, Domain domain) {
		return userDao.findByUserIdAndDomainAndDeletedFalse(userId, domain).orElse(null);
	}

	public Optional<User> getByUserIdOrExtUuid(final String userId, final String extUuid, final Domain domain) {
		final User user = getByUserId(userId, domain);
		if (user == null) {
			List<User> users = getByExtUuid(extUuid);
			if (users.size() == 1) {
				return Optional.of(users.getFirst());
			}
		}
		return Optional.ofNullable(user);
	}

	public List<User> findByCpr(String cpr) {
		return userDao.findByCprAndDeletedFalse(cpr);
	}

	@SuppressWarnings("deprecation")
	public List<User> getByRoleGroupsIncludingInactive(RoleGroup role) {
		return userDao.findByRoleGroupAssignmentsRoleGroup(role);
	}

	public List<User> getByRoleGroup(RoleGroup roleGroup) {
		return userDao.findByDeletedFalseAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false);
	}

	@SuppressWarnings("deprecation")
	public List<User> getByRolesIncludingInactive(UserRole userRole) {
		return userDao.findByUserRoleAssignmentsUserRole(userRole);
	}

	public List<User> getByDomain(Domain domain) {
		return userDao.findByDomainAndDeletedFalse(domain);
	}

	public List<User> getAll() {
		return userDao.findByDeletedFalse();
	}

	public DataTablesOutput<User> getAllAsDatatableOutput(DataTablesInput input) {
		return datatablesUserDao.findAll(input, Specification.where(DatatablesUserDao.isDeletedFalse()));
	}

	@SuppressWarnings("deprecation")
	public List<User> getAllIncludingInactive(Domain domain) {
		return userDao.findByDomain(domain);
	}

	@SuppressWarnings("deprecation")
	public List<User> getAllIncludingInactive() {
		return userDao.findAll();
	}

	@SuppressWarnings("deprecation")
	public List<User> getAllByExtUuidIncludingInactive(Set<String> extUuids) {
		return userDao.findByExtUuidIn(extUuids);
	}

	@SuppressWarnings("deprecation")
	public List<User> getAllByExtUuidIncludingInactive(Domain domain, Set<String> extUuids) {
		return userDao.findByDomainAndExtUuidIn(domain, extUuids);
	}

	public List<User> findTop10ByName(String term) {
		return userDao.findTop10ByName(term);
	}

	public List<User> getAllInactive() {
		return userDao.findByDeletedTrue();
	}

	public List<User> getAllWithNemLoginUuid() {
		return userDao.findByNemloginUuidNotNullAndDeletedFalseAndDisabledFalse();
	}

	public List<String> getAllUuidsWithNemLoginUuid() {
		return userDao.findUuidByNemloginUuidNotNullAndDeletedFalseAndDisabledFalse();
	}

	public List<User> getAllByUuidIn(Set<String> uuids) {
		return userDao.findByUuidIn(uuids);
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
		user.setDeleted(false);
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
	public void addRoleGroup(User user, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate, OrgUnit orgUnit) {
		String userFullname = SecurityUtil.getUserFullname();
		String userId = SecurityUtil.getUserId();
		UserRoleGroupAssignment assignment = new UserRoleGroupAssignment();
		assignment.setUser(user);
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByName(userFullname);
		assignment.setAssignedByUserId(userId);
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setStopDateUser(userId);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

		List<OrgUnit> orgUnits = orgUnitService.getOrgUnitsForUser(user);
		if (orgUnit != null) {
			if (orgUnits.stream().noneMatch(o -> o.getUuid().equals(orgUnit.getUuid()))) {
				assignment.setOrgUnit(orgUnits.get(0));
			} else {
				assignment.setOrgUnit(orgUnit);
			}
		} else if (!orgUnits.isEmpty()) {
			assignment.setOrgUnit(orgUnits.get(0));
		}

		user.getRoleGroupAssignments().add(assignment);
	}

	public void editRoleGroupAssignment(User user, UserRoleGroupAssignment roleGroupAssignment, LocalDate startDate, LocalDate stopDate) {
		self.editRoleGroupAssignment(user, roleGroupAssignment, startDate, stopDate, null);
	}

	@AuditLogIntercepted
	public void editRoleGroupAssignment(User user, UserRoleGroupAssignment roleGroupAssignment, LocalDate startDate, LocalDate stopDate, OrgUnit orgUnit) {
		roleGroupAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		roleGroupAssignment.setStopDate(stopDate);
		roleGroupAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

		String userId = SecurityUtil.getUserId();
		roleGroupAssignment.setStopDateUser(userId);

		List<OrgUnit> orgUnits = orgUnitService.getOrgUnitsForUser(user);
		if (orgUnit != null) {
			if (orgUnits.stream().noneMatch(o -> o.getUuid().equals(orgUnit.getUuid()))) {
				roleGroupAssignment.setOrgUnit(orgUnits.get(0));
			} else {
				roleGroupAssignment.setOrgUnit(orgUnit);
			}
		} else if (!orgUnits.isEmpty()) {
			roleGroupAssignment.setOrgUnit(orgUnits.get(0));
		}
	}

	@AuditLogIntercepted
	public boolean removeRoleGroup(User user, RoleGroup roleGroup) {
		if (user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			for (Iterator<UserRoleGroupAssignment> iterator = user.getRoleGroupAssignments().iterator(); iterator.hasNext(); ) {
				UserRoleGroupAssignment assignment = iterator.next();

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
	public void removeRoleGroupAssignment(User user, UserRoleGroupAssignment assignment) {
		for (Iterator<UserRoleGroupAssignment> iterator = user.getRoleGroupAssignments().iterator(); iterator.hasNext(); ) {
			UserRoleGroupAssignment a = iterator.next();

			if (assignment.getId() == a.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
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

	public void addUserRoleReqApprove(User user, UserRole userRole, List<RequestApprovePostponedConstraint> constraints, OrgUnit orgUnit) {
		List<PostponedConstraint> postponedConstraintsForAssignment = new ArrayList<>();
		for (RequestApprovePostponedConstraint requestConstraint : constraints) {
			SystemRole systemRole = requestConstraint.getSystemRole();
			ConstraintType constraintType = requestConstraint.getConstraintType();
			if (systemRole == null || constraintType == null) {
				continue;
			}

			PostponedConstraint postponedConstraint = new PostponedConstraint();
			postponedConstraint.setConstraintType(constraintType);
			postponedConstraint.setSystemRole(systemRole);
			postponedConstraint.setValue(requestConstraint.getValue());

			postponedConstraintsForAssignment.add(postponedConstraint);
		}
		self.addUserRole(user, userRole, null, null, postponedConstraintsForAssignment, orgUnit, true, null);
	}

	public void addUserRole(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		self.addUserRole(user, userRole, startDate, stopDate, null, null, true, null);
	}

	public void addUserRole(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate, List<PostponedConstraint> postponedConstraints) {
		self.addUserRole(user, userRole, startDate, stopDate, postponedConstraints, null, true, null);
	}

	@AuditLogIntercepted
	public void addUserRole(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate, List<PostponedConstraint> postponedConstraints, OrgUnit orgUnit, boolean notifyByEmail, String caseNumber) {
		if (userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan tildele Rollekatalog roller");
		}

		String userId = SecurityUtil.getUserId();
		UserUserRoleAssignment assignment = new UserUserRoleAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setAssignedByName(SecurityUtil.getUserFullname());
		assignment.setAssignedByUserId(SecurityUtil.getUserId());
		assignment.setAssignedTimestamp(new Date());
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		assignment.setStopDateUser(userId);
		assignment.setNotifyByEmailIfManualSystem(notifyByEmail);
		assignment.setCaseNumber(caseNumber);

		List<OrgUnit> orgUnits = orgUnitService.getOrgUnitsForUser(user);

		if (orgUnit != null) {
			if (orgUnits.stream().noneMatch(o -> o.getUuid().equals(orgUnit.getUuid()))) {
				assignment.setOrgUnit(orgUnits.get(0));
			} else {
				assignment.setOrgUnit(orgUnit);
			}
		} else if (!orgUnits.isEmpty()) {
			assignment.setOrgUnit(orgUnits.get(0));
		}

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
		editUserRoleAssignment(user, userRoleAssignment, startDate, stopDate, null, null, null);
	}

	@AuditLogIntercepted
	public void editUserRoleAssignment(User user, UserUserRoleAssignment userRoleAssignment, LocalDate startDate, LocalDate stopDate, List<PostponedConstraint> postponedConstraints) {
		editUserRoleAssignment(user, userRoleAssignment, startDate, stopDate, postponedConstraints, null, null);
	}

	@AuditLogIntercepted
	public void editUserRoleAssignment(User user, UserUserRoleAssignment userRoleAssignment, LocalDate startDate, LocalDate stopDate, List<PostponedConstraint> postponedConstraints, OrgUnit orgUnit, String caseNumber) {
		if (userRoleAssignment.getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
				&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
			throw new SecurityException("Kun administratorer kan redigere Rollekatalog roller");
		}

		userRoleAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		userRoleAssignment.setStopDate(stopDate);
		userRoleAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		userRoleAssignment.setCaseNumber(caseNumber);

		String userId = SecurityUtil.getUserId();
		userRoleAssignment.setStopDateUser(userId);

		if (postponedConstraints != null) {
			userRoleAssignment.getPostponedConstraints().removeAll(userRoleAssignment.getPostponedConstraints());

			for (PostponedConstraint postponedConstraint : postponedConstraints) {
				postponedConstraint.setUserUserRoleAssignment(userRoleAssignment);
				userRoleAssignment.getPostponedConstraints().add(postponedConstraint);
			}
		}

		List<OrgUnit> orgUnits = orgUnitService.getOrgUnitsForUser(user);
		if (orgUnit != null) {
			if (orgUnits.stream().noneMatch(o -> o.getUuid().equals(orgUnit.getUuid()))) {
				userRoleAssignment.setOrgUnit(orgUnits.get(0));
			} else {
				userRoleAssignment.setOrgUnit(orgUnit);
			}
		} else if (!orgUnits.isEmpty()) {
			userRoleAssignment.setOrgUnit(orgUnits.get(0));
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

			for (Iterator<UserUserRoleAssignment> iterator = user.getUserRoleAssignments().iterator(); iterator.hasNext(); ) {
				UserUserRoleAssignment userRoleAssignment = iterator.next();

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
	public void removeUserRoleAssignment(User user, UserUserRoleAssignment assignment) {
		for (Iterator<UserUserRoleAssignment> iterator = user.getUserRoleAssignments().iterator(); iterator.hasNext(); ) {
			UserUserRoleAssignment a = iterator.next();

			if (assignment.getId() == a.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
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
	public List<UserRole> getUserRolesAssignedDirectly(String id, Domain domain) {
		User user = userDao.findByUuidAndDeletedFalse(id).orElseGet(
				() -> userDao.findByUserIdAndDomainAndDeletedFalse(id, domain)
						.orElseThrow(() -> new NotFoundException("User with id '" + id + "' was not found in the database"))
		);
		return user.getUserRoleAssignments().stream().map(UserUserRoleAssignment::getUserRole).collect(Collectors.toList());
	}

	// TODO: this method is only used by the ReadOnlyApi, and very likely noone has any use for
	//       this method, so deprecate it in future versions of the API
	public List<RoleGroup> getRoleGroupsAssignedDirectly(String id, Domain domain) {
		User user = userDao.findByUuidAndDeletedFalse(id).orElseGet(
				() -> userDao.findByUserIdAndDomainAndDeletedFalse(id, domain)
						.orElseThrow(() -> new NotFoundException("User with id '" + id + "' was not found in the database"))
		);
		return user.getRoleGroupAssignments().stream().map(UserRoleGroupAssignment::getRoleGroup).collect(Collectors.toList());
	}

	public String getUserNameId(String userId, Domain domain) throws UserNotFoundException {
		User user = userDao.findByUserIdAndDomainAndDeletedFalse(userId, domain).orElse(null);
		if (user == null) {
			List<User> users = userDao.findByExtUuidAndDeletedFalse(userId);
			if (users.size() != 1) {
				throw new UserNotFoundException("User with userId '" + userId + "' was not found in the database");
			}
			user = users.get(0);
		}

		return "C=DK,O=" + configuration.getCustomer().getCvr() + ",CN=" + user.getName() + ",Serial=" + user.getExtUuid();
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
				assignment.setOrgUnit(roleWithConstraint.getOrgUnit());
				result.add(assignment);
			}
		}

		// user.getRoleGroups()
		if (expandRoleGroups) {
			List<UserRoleGroupAssignment> roleGroupAssignments = user.getRoleGroupAssignments().stream()
					.filter(ura -> !ura.isInactive())
					.toList();

			for (UserRoleGroupAssignment roleGroupAssignment : roleGroupAssignments) {
				RoleGroup roleGroup = roleGroupAssignment.getRoleGroup();
				List<UserRoleWithAssignmentIdDTO> ur = roleGroup.getUserRoleAssignments().stream().map(ura -> new UserRoleWithAssignmentIdDTO(ura)).collect(Collectors.toList());

				for (UserRoleWithAssignmentIdDTO roleWithId : ur) {
					UserRole role = roleWithId.getUserRole();
					if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
						UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
						assignment.setUserRole(role);
						assignment.setOrgUnit(roleGroupAssignment.getOrgUnit());
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
			List<PositionUserRoleAssignment> positionAssignments = position.getUserRoleAssignments().stream()
					.filter(ura -> !ura.isInactive())
					.collect(Collectors.toList());

			for (PositionUserRoleAssignment positionAssignment : positionAssignments) {
				UserRole role = positionAssignment.getUserRole();
				if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
					UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
					assignment.setUserRole(role);
					assignment.setAssignmentId(positionAssignment.getId());
					assignment.setFromPosition(true);
					assignment.setOrgUnit(positionAssignment.getPosition().getOrgUnit());

					result.add(assignment);
				}
			}

			// position.getRoleGroups
			if (expandRoleGroups) {
				List<RoleGroup> prg = position.getRoleGroupAssignments().stream()
						.filter(ura -> !ura.isInactive())
						.map(PositionRoleGroupAssignment::getRoleGroup)
						.toList();

				for (RoleGroup roleGroup : prg) {
					for (RoleGroupUserRoleAssignment rgAssignment : roleGroup.getUserRoleAssignments()) {
						UserRole role = rgAssignment.getUserRole();
						if (itSystem == null || role.getItSystem().getId() == itSystem.getId()) {
							UserRoleAssignmentWithInfo assignment = new UserRoleAssignmentWithInfo();
							assignment.setUserRole(role);
							assignment.setAssignedThroughInfo(new AssignedThroughInfo(roleGroup));
							assignment.setAssignmentId(rgAssignment.getId());
							assignment.setFromPosition(true);

							result.add(assignment);
						}
					}
				}
			}

			if (!position.isDoNotInherit() && position.getTitle() != null) {

				// position.title (userRoles)

				List<OrgUnitUserRoleAssignment> assignments = position.getOrgUnit().getUserRoleAssignments()
						.stream()
						.filter(ura -> (ura.getContainsTitles() == ContainsTitles.POSITIVE && ura.getTitles().contains(position.getTitle()))
								|| (ura.getContainsTitles() == ContainsTitles.NEGATIVE && !ura.getTitles().contains(position.getTitle())))
						.toList();

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
							.filter(ura -> (ura.getContainsTitles() == ContainsTitles.POSITIVE && ura.getTitles().contains(position.getTitle()))
									|| (ura.getContainsTitles() == ContainsTitles.NEGATIVE && !ura.getTitles().contains(position.getTitle())))
							.toList();

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
								uratu.setAssignedThroughInfo(new AssignedThroughInfo(position.getOrgUnit(), position.getTitle(), RoleType.ROLEGROUP));
								uratu.setAssignmentId(roleWithId.getAssignmentId());

								result.add(uratu);
							}
						}
					}

				}
			}

			if (!position.isDoNotInherit()) {
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
		List<UserUserRoleAssignment> userRoleAssignments = user.getUserRoleAssignments().stream().toList();

		result.addAll(userRoleAssignments.stream().map(RoleAssignedToUserDTO::fromUserRoleAssignment).toList());

		// user.getRoleGroups()
		List<UserRoleGroupAssignment> roleGroupAssignments = user.getRoleGroupAssignments().stream().toList();

		result.addAll(roleGroupAssignments.stream().map(RoleAssignedToUserDTO::fromRoleGroupAssignment).toList());

		// user.getPositions() -> everything assigned through these
		for (Position position : user.getPositions()) {

			// position.getRoles();
			List<PositionUserRoleAssignment> pur = position.getUserRoleAssignments().stream().toList();

			result.addAll(pur.stream().map(RoleAssignedToUserDTO::fromPositionUserRoleAssignment).toList());

			// position.getRoleGroups
			List<PositionRoleGroupAssignment> prg = position.getRoleGroupAssignments().stream().toList();

			result.addAll(prg.stream().map(RoleAssignedToUserDTO::fromPositionRoleGroupAssignment).toList());

			if (!position.isDoNotInherit()) {
				// recursive through all OrgUnits from here and up
				getAllUserRolesAndRoleGroupsFromOrgUnit(result, position.getOrgUnit(), false, user, position.getTitle());
			}
		}

		// expand rolegroups
		Set<Long> seenRoleGroups = new HashSet<>();
		List<RoleAssignedToUserDTO> expanded = new ArrayList<>();
		for (RoleAssignedToUserDTO assignment : result) {
			if (assignment.getType().equals(RoleAssignmentType.ROLEGROUP)) {
				// no reason to expand the same RoleGroup multiple times
				if (seenRoleGroups.contains(assignment.getRoleId())) {
					continue;
				}

				seenRoleGroups.add(assignment.getRoleId());

				RoleGroup roleGroup = roleGroupDao.findById(assignment.getRoleId()).orElse(null);

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
			if(result.stream().anyMatch(dto -> dto.getAssignmentId() == roleMapping.getId())) {
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

			// Filter out assignments through title that don't include the user's title
			if (roleMapping.getContainsTitles() == ContainsTitles.POSITIVE && !roleMapping.getTitles().contains(title)) {
				continue;
			}
			//Checks if any of titles are present
			else if (roleMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
				Optional<Title> negativeMatch = roleMapping.getTitles().stream().filter(negativeTitle -> negativeTitle.equals(title)).findAny();
				if (negativeMatch.isPresent()) {
					continue;
				}
			}

			result.add(RoleAssignedToUserDTO.fromOrgUnitUserRoleAssignment(roleMapping));
		}

		// ou.getRoleGroups()
		for (OrgUnitRoleGroupAssignment roleGroupMapping : orgUnit.getRoleGroupAssignments()) {
			if(result.stream().anyMatch(dto -> dto.getAssignmentId() == roleGroupMapping.getId())) {
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

			// Filter out assignments through title that don't include the user's title
			if (roleGroupMapping.getContainsTitles() == ContainsTitles.POSITIVE && !roleGroupMapping.getTitles().contains(title)) {
				continue;
			}
			//Checks if any of titles are present
			else if (roleGroupMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
				Optional<Title> negativeMatch = roleGroupMapping.getTitles().stream().filter(negativeTitle -> negativeTitle.equals(title)).findAny();
				if (negativeMatch.isPresent()) {
					continue;
				}
			}

			result.add(RoleAssignedToUserDTO.fromOrgUnitRoleGroupAssignment(roleGroupMapping));
		}

		// recursive upwards in the hierarchy (only inherited roles)
		if (orgUnit.getParent() != null) {
			getAllUserRolesAndRoleGroupsFromOrgUnit(result, orgUnit.getParent(), true, user, title);
		}
	}

	public List<RoleAssignedToUserDTO> getAllNegativeUserRolesAndRoleGroups(User user) {
		List<RoleAssignedToUserDTO> result = new ArrayList<>();

		for (Position position : user.getPositions()) {
			if (!position.isDoNotInherit()) {
				// recursive through all OrgUnits from here and up
				getAllNegativeUserRolesAndRoleGroupsFromOrgUnit(result, position.getOrgUnit(), false, user, position.getTitle());
			}
		}
		return new ArrayList<>(result);
	}

	private void getAllNegativeUserRolesAndRoleGroupsFromOrgUnit(List<RoleAssignedToUserDTO> result, OrgUnit orgUnit, boolean inheritOnly, User user, Title title) {
		for (OrgUnitUserRoleAssignment roleMapping : orgUnit.getUserRoleAssignments()) {
			if (inheritOnly && !roleMapping.isInherit()) {
				continue;
			}

			if (roleMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
				Optional<Title> negativeMatch = roleMapping.getTitles().stream().filter(negativeTitle -> negativeTitle.equals(title)).findAny();
				if (negativeMatch.isPresent()) {
					result.add(RoleAssignedToUserDTO.fromNegativeOrgUnitUserRoleAssignment(roleMapping));
				}
			}
		}

		for (OrgUnitRoleGroupAssignment roleMapping : orgUnit.getRoleGroupAssignments()) {
			if (inheritOnly && !roleMapping.isInherit()) {
				continue;
			}

			if (roleMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
				Optional<Title> negativeMatch = roleMapping.getTitles().stream().filter(negativeTitle -> negativeTitle.equals(title)).findAny();
				if (negativeMatch.isPresent()) {
					result.add(RoleAssignedToUserDTO.fromNegativeOrgUnitRoleGroupAssignment(roleMapping));
				}

			}
		}
		// recursive upwards in the hierarchy (only inherited roles)
		if (orgUnit.getParent() != null) {
			getAllNegativeUserRolesAndRoleGroupsFromOrgUnit(result, orgUnit.getParent(), true, user, title);
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
		List<RoleGroupAssignedToUser> result = new ArrayList<>();

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

			if (!position.isDoNotInherit() && position.getTitle() != null) {

				// position.title (RoleGroups)
				List<OrgUnitRoleGroupAssignment> titleAssignments = position.getOrgUnit().getRoleGroupAssignments()
						.stream()
						.filter(ura -> (ura.getContainsTitles() == ContainsTitles.POSITIVE && ura.getTitles().contains(position.getTitle()))
								|| (ura.getContainsTitles() == ContainsTitles.NEGATIVE && !ura.getTitles().contains(position.getTitle())))
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
			if (!position.isDoNotInherit()) {
				getAllRoleGroupsFromOrgUnit(result, position.getOrgUnit(), false, user);
			}
		}

		return new ArrayList<>(result);
	}

	public void addPostponedListsToModel(Model model) {
		List<Kle> kles = kleService.findAll();
		List<KleDTO> kleConstraintDTOS = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());

			String code = kle.getCode().replaceAll("\\.\\*", "");
			kleDTO.setText(kle.isActive() ? code + " " + kle.getName() : code + " " + kle.getName() + " [UDGÅET]");
			kleConstraintDTOS.add(kleDTO);
		}

		model.addAttribute("kleList", kleConstraintDTOS);

		List<OUListForm> treeOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, false))
				.sorted(Comparator.comparing(OUListForm::getText))
				.collect(Collectors.toList());

		model.addAttribute("treeOUs", treeOUs);
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

			if (roleGroupMapping.getContainsTitles() == ContainsTitles.POSITIVE) {
				boolean match = roleGroupMapping.getTitles()
						.stream()
						.anyMatch(t -> userHasTitle(user, t.getUuid()));
				if (!match) {
					continue;
				}
			}

			if (roleGroupMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
				boolean match = roleGroupMapping.getTitles()
						.stream()
						.anyMatch(t -> userHasTitle(user, t.getUuid()));
				// Negative assignment case, bail out if matching
				if (match) {
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

	private boolean userHasTitle(User user, String titleUuid) {
		return user.getPositions()
				.stream()
				.anyMatch(p -> p.getTitle() != null && Objects.equals(p.getTitle().getUuid(), titleUuid));
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

			if (roleMapping.getContainsTitles() == ContainsTitles.POSITIVE) {
				boolean match = roleMapping.getTitles().stream()
						.anyMatch(t -> userHasTitle(user, t.getUuid()));

				if (!match) {
					continue;
				}
			}

			if (roleMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
				boolean match = roleMapping.getTitles().stream()
						.anyMatch(t -> userHasTitle(user, t.getUuid()));
				if (match) {
					// Matching title means exclude!
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

				if (roleGroupMapping.getContainsTitles() == ContainsTitles.POSITIVE) {
					boolean match = roleGroupMapping.getTitles()
							.stream()
							.anyMatch(t -> userHasTitle(user, t.getUuid()));
					if (!match) {
						continue;
					}
				}

				if (roleGroupMapping.getContainsTitles() == ContainsTitles.NEGATIVE) {
					boolean match = roleGroupMapping.getTitles()
							.stream()
							.anyMatch(t -> userHasTitle(user, t.getUuid()));
					// Matching title means exclude!
					if (match) {
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
			if (assignment.getUser().isDeleted()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(assignment.getUser().getName());
			mapping.setUserUserId(assignment.getUser().getUserId());
			mapping.setUserUuid(assignment.getUser().getUuid());
			mapping.setUserUuid(assignment.getUser().getExtUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromRoleGroupAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);
			mapping.setPositions(assignment.getUser().getPositions());

			result.add(mapping);
		}

		for (PositionRoleGroupAssignment assignment : positionRoleGroupAssignmentDao.findByRoleGroup(roleGroup)) {
			User user = assignment.getPosition().getUser();
			if (user.isDeleted()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(user.getName());
			mapping.setUserUserId(user.getUserId());
			mapping.setUserUuid(user.getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromPositionRoleGroupAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);
			mapping.setPositions(user.getPositions());

			result.add(mapping);
		}

		return result;
	}

	public List<UserWithRole2> getActiveUsersWithUserRole(UserRole userRole) {
		List<UserWithRole2> result = new ArrayList<>();

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		for (UserUserRoleAssignment assignment : userUserRoleAssignmentDao.findByUserRole(userRole)) {
			User user = assignment.getUser();
			if (user.isDeleted()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(user.getName());
			mapping.setUserUserId(user.getUserId());
			mapping.setUserUuid(user.getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromUserRoleAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);
			mapping.setPositions(user.getPositions());

			result.add(mapping);
		}

		for (PositionUserRoleAssignment assignment : positionUserRoleAssignmentDao.findByUserRole(userRole)) {
			User user = assignment.getPosition().getUser();
			if (user.isDeleted()) {
				continue;
			}
			UserWithRole2 mapping = new UserWithRole2();
			mapping.setUserName(user.getName());
			mapping.setUserUserId(user.getUserId());
			mapping.setUserUuid(user.getUuid());
			mapping.setAssignment(RoleAssignedToUserDTO.fromPositionUserRoleAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);
			mapping.setPositions(user.getPositions());

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

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		// this we ALWAYS need to do
		for (User user : userDao.findByDeletedFalseAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, false)) {
			UserWithRole mapping = new UserWithRole();
			mapping.setUser(user);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);
			UserUserRoleAssignment userRoleAssignment = user.getUserRoleAssignments().stream().filter(ura -> ura.getUserRole() == userRole).findAny()
					.orElseThrow(() -> new RuntimeException("UserRole " + userRole.getId() + " should be asigned to user with id: " + user.getUuid()));
			mapping.setAssignment(RoleAssignedToUserDTO.fromUserRoleAssignment(userRoleAssignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);
		}

		// get rolegroups that have the userrole included
		List<RoleGroup> roleGroups = roleGroupDao.findByUserRoleAssignmentsUserRole(userRole);

		// get users that have roleGroup assigned
		for (RoleGroup roleGroup : roleGroups) {
			for (User user : userDao.findByDeletedFalseAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false)) {
				UserWithRole mapping = new UserWithRole();
				mapping.setUser(user);
				mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);
				UserRoleGroupAssignment roleGroupAssignment = user.getRoleGroupAssignments().stream().filter(rga -> rga.getRoleGroup() == roleGroup).findAny()
						.orElseThrow(() -> new RuntimeException("RoleGroup " + roleGroup.getId() + " should be assigned to user with uuid: " + user.getUuid()));
				mapping.setAssignment(RoleAssignedToUserDTO.fromRoleGroupAssignment(roleGroupAssignment));
				mapping.getAssignment().setCanEdit(false);

				result.add(mapping);
			}
		}

		if (findIndirectlyAssignedRoles) {

			// titles or position assignements (depending on configuration)
			if (!configuration.getTitles().isEnabled()) {
				// get positions that have userRole assigned
				for (PositionUserRoleAssignment assignment : positionUserRoleAssignmentDao.findByUserRole(userRole)) {
					if (assignment.getPosition().getUser().isDeleted()) {
						continue;
					}
					UserWithRole mapping = new UserWithRole();
					mapping.setUser(assignment.getPosition().getUser());
					mapping.setAssignedThrough(AssignedThrough.POSITION);
					mapping.setAssignment(RoleAssignedToUserDTO.fromPositionUserRoleAssignment(assignment));
					mapping.getAssignment().setCanEdit(false);

					result.add(mapping);
				}

//				// get positions that have roleGroup assigned
				for (RoleGroup roleGroup : roleGroups) {
					for (PositionRoleGroupAssignment assignment : positionRoleGroupAssignmentDao.findByRoleGroup(roleGroup)) {
						if (assignment.getPosition().getUser().isDeleted()) {
							continue;
						}
						UserWithRole mapping = new UserWithRole();
						mapping.setUser(assignment.getPosition().getUser());
						mapping.setAssignedThrough(AssignedThrough.POSITION);
						mapping.setAssignment(RoleAssignedToUserDTO.fromPositionRoleGroupAssignment(assignment));
						mapping.getAssignment().setCanEdit(false);

						result.add(mapping);
					}
				}
			}

			// get ous that have userRole assigned
			for (OrgUnit orgUnit : orgUnitService.getByUserRole(userRole, false)) {

				for (OrgUnitUserRoleAssignment orgUnitUserRoleAssignment : orgUnit.getUserRoleAssignments()) {

					// skip irrelevant assignments
					if (!Objects.equals(orgUnitUserRoleAssignment.getUserRole().getId(), userRole.getId())) {
						continue;
					}

					// if titles are disabled and this assignment contains titles, skip it
					if (!configuration.getTitles().isEnabled() && orgUnitUserRoleAssignment.getContainsTitles() != ContainsTitles.NO) {
						continue;
					}

					// Get list of excepted users (if any)
					ArrayList<String> exceptedUsersUuid = new ArrayList<>();
					if (orgUnitUserRoleAssignment.isContainsExceptedUsers()) {
						exceptedUsersUuid.addAll(orgUnitUserRoleAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
					}

					for (Position position : positionService.findByOrgUnit(orgUnit)) {
						if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
							continue;
						}

						if ((orgUnitUserRoleAssignment.getContainsTitles() == ContainsTitles.POSITIVE && !orgUnitUserRoleAssignment.getTitles().contains(position.getTitle()))
								|| (orgUnitUserRoleAssignment.getContainsTitles() == ContainsTitles.NEGATIVE && orgUnitUserRoleAssignment.getTitles().contains(position.getTitle()))) {
							continue;
						}

						if (!position.getUser().isDeleted() && !position.isDoNotInherit()) {
							UserWithRole mapping = new UserWithRole();
							mapping.setUser(position.getUser());

							if (orgUnitUserRoleAssignment.getContainsTitles() != ContainsTitles.NO) {
								mapping.setAssignedThrough(AssignedThrough.TITLE);
							} else {
								mapping.setAssignedThrough(AssignedThrough.ORGUNIT);
							}

							mapping.setAssignment(RoleAssignedToUserDTO.fromOrgUnitUserRoleAssignment(orgUnitUserRoleAssignment));
							mapping.getAssignment().setCanEdit(false);

							result.add(mapping);
						}
					}

					// check if the assignment to the OrgUnit is flagged with inherit
					if (orgUnitUserRoleAssignment.isInherit()) {
						List<UserWithRole> inherited = getUserRoleMappingsRecursive(orgUnit.getChildren(),
								RoleAssignedToUserDTO.fromOrgUnitUserRoleAssignment(orgUnitUserRoleAssignment), AssignedThrough.ORGUNIT);
						result.addAll(inherited);
					}
				}
			}

			// get ous that have roleGroup assigned
			for (RoleGroup roleGroup : roleGroups) {

				for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {

					for (OrgUnitRoleGroupAssignment orgUnitRoleGroupAssignment : orgUnit.getRoleGroupAssignments()) {

						// skip irrelevant assignments
						if (!Objects.equals(orgUnitRoleGroupAssignment.getRoleGroup().getId(), roleGroup.getId())) {
							continue;
						}

						// if titles are disabled and this assignment contains titles, skip it
						if (!configuration.getTitles().isEnabled() && orgUnitRoleGroupAssignment.getContainsTitles() != ContainsTitles.NO) {
							continue;
						}

						// Get list of excepted users (if any)
						ArrayList<String> exceptedUsersUuid = new ArrayList<>();
						if (orgUnitRoleGroupAssignment.isContainsExceptedUsers()) {
							exceptedUsersUuid.addAll(orgUnitRoleGroupAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
						}

						for (Position position : positionService.findByOrgUnit(orgUnit)) {
							if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
								continue;
							}

							if ((orgUnitRoleGroupAssignment.getContainsTitles() == ContainsTitles.POSITIVE && !orgUnitRoleGroupAssignment.getTitles().contains(position.getTitle())) || (orgUnitRoleGroupAssignment.getContainsTitles() == ContainsTitles.NEGATIVE && orgUnitRoleGroupAssignment.getTitles().contains(position.getTitle()))) {
								continue;
							}

							if (!position.getUser().isDeleted() && !position.isDoNotInherit()) {
								UserWithRole mapping = new UserWithRole();
								mapping.setUser(position.getUser());
								mapping.setAssignment(RoleAssignedToUserDTO.fromOrgUnitRoleGroupAssignment(orgUnitRoleGroupAssignment));
								if (orgUnitRoleGroupAssignment.getContainsTitles() != ContainsTitles.NO) {
									mapping.setAssignedThrough(AssignedThrough.TITLE);
								} else {
									mapping.setAssignedThrough(AssignedThrough.ORGUNIT);
								}

								result.add(mapping);
							}
						}

						// check if the assignment to the OrgUnit is flagged with inherit
						if (orgUnitRoleGroupAssignment.isInherit()) {
							List<UserWithRole> inherited = getUserRoleMappingsRecursive(orgUnit.getChildren(), RoleAssignedToUserDTO.fromOrgUnitRoleGroupAssignment(orgUnitRoleGroupAssignment), AssignedThrough.ORGUNIT);
							result.addAll(inherited);
						}
					}
				}
			}
		}

		return result;
	}

	public boolean isVikar(String userId) {
		String vikarRegEx = settingsService.getVikarRegEx();
		if (StringUtils.hasLength(userId) && StringUtils.hasLength(vikarRegEx)) {
			return userId.toLowerCase().matches(vikarRegEx);
		}
		
		return false;
	}

	public List<UserWithRoleAndDates> getUsersWithUserRoleDirectlyAssigned(UserRole userRole) {
		List<UserWithRoleAndDates> result = new ArrayList<>();

		// this we ALWAYS need to do
		@SuppressWarnings("deprecation") // ok, it is for UI
		List<User> userRoleUsers = userDao.findByDeletedFalseAndUserRoleAssignmentsUserRole(userRole);
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
			List<User> roleGroupUsers = userDao.findByDeletedFalseAndRoleGroupAssignmentsRoleGroup(roleGroup);
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

	private List<UserWithRole> getUserRoleMappingsRecursive(List<OrgUnit> children, RoleAssignedToUserDTO inheritedAssignmentDTO, AssignedThrough assignedThrough) {
		List<UserWithRole> result = new ArrayList<>();

		if (children != null) {
			for (OrgUnit child : children) {
				if (!orgUnitService.isActiveAndIncluded(child)) {
					continue;
				}

				for (Position position : positionService.findByOrgUnit(child)) {
					if (!position.getUser().isDeleted()
							&& !position.isDoNotInherit()
							&& !shouldAssignmentBeExcludedBasedOnPosition(position, inheritedAssignmentDTO)) {

						UserWithRole mapping = new UserWithRole();
						mapping.setUser(position.getUser());
						mapping.setAssignedThrough(assignedThrough);
						mapping.setAssignment(inheritedAssignmentDTO);
						mapping.getAssignment().setCanEdit(false);

						result.add(mapping);
					}
				}

				List<UserWithRole> inherited = getUserRoleMappingsRecursive(child.getChildren(), inheritedAssignmentDTO, assignedThrough);
				result.addAll(inherited);
			}
		}

		return result;
	}

	/**
	 * Check if assignments on this position should be excluded.
	 * Currently, checks if the users position contains title that are excluded by negative assignment
	 */
	private boolean shouldAssignmentBeExcludedBasedOnPosition(final Position position, final RoleAssignedToUserDTO assignmnet) {
		if (position.getTitle() != null && position.getUser() != null) {
			if (Objects.equals(assignmnet.getContainsTitles(), ContainsTitles.NEGATIVE)) {
				return (assignmnet.getTitleUuids() != null && assignmnet.getTitleUuids().contains(position.getTitle().getUuid()));
			} else if (Objects.equals(assignmnet.getContainsTitles(), ContainsTitles.POSITIVE)) {
				return (assignmnet.getTitleUuids() != null && !assignmnet.getTitleUuids().contains(position.getTitle().getUuid()));
			}
		}
		return false;
	}

	public List<UserWithRole> getUsersWithRoleGroup(RoleGroup roleGroup, boolean findIndirectlyAssignedRoleGroups) {
		List<UserWithRole> result = new ArrayList<>();

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		// get users that have roleGroup assigned
		for (User user : userDao.findByDeletedFalseAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false)) {
			UserWithRole mapping = new UserWithRole();
			mapping.setUser(user);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			UserRoleGroupAssignment roleGroupAssignment = user.getRoleGroupAssignments().stream().filter(rga -> rga.getRoleGroup() == roleGroup).findAny()
					.orElseThrow(() -> new RuntimeException("RoleGroup " + roleGroup.getId() + " should be assigned to user with uuid: " + user.getUuid()));
			mapping.setAssignment(RoleAssignedToUserDTO.fromRoleGroupAssignment(roleGroupAssignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);
		}

		if (findIndirectlyAssignedRoleGroups) {
			if (!configuration.getTitles().isEnabled()) {
				// get positions that have roleGroup assigned
				for (PositionRoleGroupAssignment assignment : positionRoleGroupAssignmentDao.findByRoleGroup(roleGroup)) {
					if (assignment.getPosition().getUser().isDeleted()) {
						continue;
					}
					UserWithRole mapping = new UserWithRole();
					mapping.setUser(assignment.getPosition().getUser());
					mapping.setAssignedThrough(AssignedThrough.POSITION);
					mapping.setAssignment(RoleAssignedToUserDTO.fromPositionRoleGroupAssignment(assignment));
					mapping.getAssignment().setCanEdit(false);

					result.add(mapping);
				}
			}

			// get ous that have roleGroup assigned
			for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {
				List<OrgUnitRoleGroupAssignment> roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(ourga -> Objects.equals(ourga.getRoleGroup().getId(), roleGroup.getId())).collect(Collectors.toList());
				if (roleGroupAssignments.isEmpty()) {
					log.error("getUsersWithRoleGroup: Could not find assignment (RoleGroupId: " + roleGroup.getId() + ", OU: " + orgUnit.getUuid() + ")");
					continue;
				}

				for (OrgUnitRoleGroupAssignment orgUnitRoleGroupAssignment : roleGroupAssignments) {
					// Get list of excepted users (if any)
					ArrayList<String> exceptedUsersUuid = new ArrayList<>();
					if (orgUnitRoleGroupAssignment.isContainsExceptedUsers()) {
						exceptedUsersUuid.addAll(orgUnitRoleGroupAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList()));
					}

					ArrayList<String> titles = new ArrayList<>();
					if (configuration.getTitles().isEnabled() && orgUnitRoleGroupAssignment.getContainsTitles() != ContainsTitles.NO) {
						titles.addAll(orgUnitRoleGroupAssignment.getTitles().stream().map(Title::getUuid).collect(Collectors.toList()));
					}

					for (Position position : positionService.findByOrgUnit(orgUnit)) {
						// exceptedUsers, and user is mentioned, skip
						if (exceptedUsersUuid.contains(position.getUser().getUuid())) {
							continue;
						}

						// titles enabled and title assignment, but not title-match, skip
						if (configuration.getTitles().isEnabled() && orgUnitRoleGroupAssignment.getContainsTitles() == ContainsTitles.POSITIVE) {
							if (position.getTitle() == null || !titles.contains(position.getTitle().getUuid())) {
								continue;
							}
						}
						if (configuration.getTitles().isEnabled() && orgUnitRoleGroupAssignment.getContainsTitles() == ContainsTitles.NEGATIVE) {
							if (position.getTitle() != null && titles.contains(position.getTitle().getUuid())) {
								continue;
							}
						}

						if (!position.getUser().isDeleted() && !position.isDoNotInherit()) {
							UserWithRole mapping = new UserWithRole();
							mapping.setUser(position.getUser());
							mapping.setAssignedThrough(AssignedThrough.ORGUNIT);

							mapping.setAssignment(RoleAssignedToUserDTO.fromOrgUnitRoleGroupAssignment(orgUnitRoleGroupAssignment));
							mapping.getAssignment().setCanEdit(false);

							result.add(mapping);
						}
					}

					// check if the assignment to the OrgUnit is flagged with inherit
					if (orgUnitRoleGroupAssignment.isInherit()) {
						List<UserWithRole> inherited = getUserRoleMappingsRecursive(orgUnit.getChildren(),
								RoleAssignedToUserDTO.fromOrgUnitRoleGroupAssignment(orgUnitRoleGroupAssignment),
								AssignedThrough.ORGUNIT);
						result.addAll(inherited);
					}
				}
			}
		}

		return result;
	}

	public String generateOIOBPP(User user, List<ItSystem> itSystems, Map<String, String> roleMap) throws UserNotFoundException {
		// santity check - this happens a bit to often
		if (!StringUtils.hasLength(configuration.getIntegrations().getKombit().getDomain())) {
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
					builder.append("<Constraint Name=\"" + constraint.getParameter() + "\">" + StringEscapeUtils.escapeXml10(constraint.getValue()) + "</Constraint>");
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
			} else {
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
								} else if (constraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getOrganisationConstraint(user, false));
								}
								break;
							case EXTENDED_INHERITED:
								if (constraint.getConstraintType().getEntityId().equals(Constants.KLE_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getKLEConstraint(user, ConstraintValueType.EXTENDED_INHERITED));
								} else if (constraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {
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
							case LEVEL_5:
							case LEVEL_6:
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
										if (postponedConstraint.getConstraintType().getId() == constraint.getConstraintType().getId() &&
												postponedConstraint.getSystemRole().getId() == constraint.getSystemRoleAssignment().getSystemRole().getId()) {

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
			case LEVEL_5:
			case LEVEL_6:
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

	private String getOrganisationConstraint(User user, ConstraintValueType constraintValueType) {
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
								constraintValueType.equals(ConstraintValueType.LEVEL_4) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_5) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_6)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case LEVEL_3:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_3) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_4) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_5) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_6)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case LEVEL_4:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_4) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_5) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_6)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case LEVEL_5:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_5) ||
								constraintValueType.equals(ConstraintValueType.LEVEL_6)) {
							orgUnitWithRequiredLevel = ou;
						}
						break;
					case LEVEL_6:
						if (constraintValueType.equals(ConstraintValueType.LEVEL_6)) {
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
			} else {
				result.add(position.getOrgUnit().getUuid());
			}
		}

		return String.join(",", result);
	}

	private String getOrganisationConstraint(User user, boolean extended) {
		Set<String> result = new HashSet<>();

		for (Position position : user.getPositions()) {
			OrgUnit orgUnit = position.getOrgUnit();

			if (extended) {
				appendThisAndChildren(orgUnit, result);
			} else {
				result.add(orgUnit.getUuid());
			}
		}

		return String.join(",", result);
	}

	private void appendThisAndChildren(OrgUnit orgUnit, Set<String> result) {
		result.add(orgUnit.getUuid());

		appendChildren(orgUnit, result);
	}

	private void appendChildren(OrgUnit orgUnit, Set<String> result) {
		for (OrgUnit child : orgUnit.getChildren()) {
			if (!orgUnitService.isActiveAndIncluded(child)) {
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
			for (int j = i + 1; j < usersFlatArray.length; j++) {
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
			if (!position.getUser().isDeleted()) {
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

	public boolean hasCicsUser(User user) {
		if (user == null || user.getAltAccounts() == null || user.getAltAccounts().size() == 0) {
			return false;
		}

		return user.getAltAccounts().stream().anyMatch(a -> a.getAccountType().equals(AltAccountType.KSPCICS));
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
		return userDao.getTopByDeletedFalseOrderByLastUpdatedDesc();
	}

	public void removeAllDirectlyAssignedRoles(User user) {
		while (!user.getUserRoleAssignments().isEmpty()) {
			UserRole role = user.getUserRoleAssignments().get(0).getUserRole();
			removeUserRole(user, role);
			log.info("Removing userRole '" + role.getItSystem().getName() + "/" + role.getName() + " (" + role.getId() + ")' from user '" + user.getUserId() + "'");
		}

		while (!user.getRoleGroupAssignments().isEmpty()) {
			RoleGroup role = user.getRoleGroupAssignments().get(0).getRoleGroup();
			removeRoleGroup(user, role);
			log.info("Removing roleGroup '" + role.getName() + " (" + role.getId() + ")' from user '" + user.getUserId() + "'");
		}
	}

	public List<User> getSubstitutesManager(User user) {
		return userDao.findByManagerSubstitutesSubstitute(user);
	}

	public boolean isSystemOwnerOrAttestationResponsible(User user) {
		return !itSystemService.findByAttestationResponsibleOrSystemOwner(user).isEmpty();
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

	public List<User> findManagers() {
		return orgUnitService.getAllWithManager().stream()
				.map(o -> o.getManager())
				.filter(StreamExtensions.distinctByKey(User::getUuid))
				.collect(Collectors.toList());
	}

	@Transactional(rollbackFor = Exception.class)
	public void removeInactiveSubstituteManagers() {
		List<User> usersWithInactiveSubstitute = userDao.findByManagerSubstitutesSubstituteDeletedTrue();

		for (User user : usersWithInactiveSubstitute) {
			for (Iterator<ManagerSubstitute> iterator = user.getManagerSubstitutes().iterator(); iterator.hasNext(); ) {
				ManagerSubstitute mapping = iterator.next();

				if (mapping.getSubstitute().isDeleted()) {
					log.info("Removing inactive Substitute: " + mapping.getSubstitute().getName());
					iterator.remove();
				}
			}
		}

		self.save(usersWithInactiveSubstitute);
	}

	public List<User> searchUsers(String query) {
		return userDao.findByNameContainsOrUserIdContainsAndDeletedFalse(query, query);
	}

	public List<User> findTop10() {
		return userDao.findTop10ByDeletedFalse();
	}
}
