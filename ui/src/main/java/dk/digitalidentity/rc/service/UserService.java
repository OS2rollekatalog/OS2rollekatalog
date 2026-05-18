package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.DatatablesUserDao;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.event.UpdateUserAssignmentsMessage;
import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.Constraint;
import dk.digitalidentity.rc.service.model.KleAssignment;
import dk.digitalidentity.rc.service.model.Privilege;
import dk.digitalidentity.rc.service.model.PrivilegeGroup;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import dk.digitalidentity.rc.util.OrganisationConstraintUtil;
import dk.digitalidentity.rc.util.StreamExtensions;
import dk.digitalidentity.simple_queue.BulkQueueMessage;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import dk.digitalidentity.simple_queue.service.QueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.event.AssignmentChangeEventHandler.ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER;

// TODO IMPORTANT: Do not add get role assignment methods to this service - use assignmentService
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
	private UserService self;

	@Autowired
	private DomainService domainService;

	@Autowired
	private DatatablesUserDao datatablesUserDao;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private OrganisationConstraintUtil organisationConstraintUtil;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	@Autowired
	private QueueService queueService;

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

	public List<User> getByExtUuid(String uuid) {
		return userDao.findByExtUuidAndDeletedFalse(uuid);
	}

	// Please use the version returning optional instead
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

	public List<User> getByDomain(Domain domain) {
		return userDao.findByDomainAndDeletedFalse(domain);
	}

	public List<User> getAll() {
		return userDao.findByDeletedFalse();
	}

	@Transactional
	public List<User> getAll(Consumer<User> consumer) {
		List<User> users = userDao.findByDeletedFalse();

		if (consumer != null) {
			users.forEach(consumer);
		}

		return users;
	}

	public DataTablesOutput<User> getAllAsDatatableOutput(DataTablesInput input) {
		return datatablesUserDao.findAll(input, DatatablesUserDao.isDeletedFalse());
	}

	@SuppressWarnings("deprecation")
	public List<User> getAllIncludingInactive(Domain domain) {
		return userDao.findByDomain(domain);
	}

	@SuppressWarnings("deprecation")
	public List<User> getAllIncludingInactive() {
		return userDao.findAll();
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

	public List<User> getAllByUuidIn(Set<String> uuids) {
		return userDao.findByUuidIn(uuids);
	}


	/**
	 * Determines if a user is considered stale by checking if there are any active messages
	 * associated with the user's UUID in the assignment update queue.
	 *
	 * @param userUuid the unique identifier of the user to check for staleness
	 * @return true if the user is stale (no active messages in the queue), false otherwise
	 */
	public boolean isUserStale(final String userUuid) {
		return queueService.isMessageActive(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER, userUuid);
	}


	// utility methods

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public void queueAllForRecalculation() {
		userDao.findByDeletedFalse().forEach(this::queueForRecalculation);
	}

	// Lookup must include deleted users: the @Before activateUser hook fires before deleted is
	// flipped to false, so a deleted-filtered query would skip users being reactivated and they
	// would never get their assignments recalculated. The queue consumer handles either state.
	public void queueForRecalculation(final String userUuid) {
		userDao.findByUuid(userUuid)
			.ifPresent(this::queueForRecalculation);
	}

	public void queueForRecalculation(final User user) {
		eventPublisher.publishEvent(QueueMessage.builder()
			.queue(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
			.messageId(user.getUuid()) // Use user uuid as messageId, this ensure we do not get any duplicates in the queue
			.priority(10L)
			.dequeueTime(LocalDateTime.now().plusSeconds(2)) // Allow a few seconds before processing the message, so we are sure the changes to the assignments are persisted.
			.body(JsonSimpleMessage.toJson(createUserUpdateMessage(user.getUuid())))
			.build());
	}


	public void queueMultipleForRecalculation(final Set<String> userUuids) {
		List<QueueMessage> messages = userUuids.parallelStream()
			.filter(Objects::nonNull)
			.map(uuid -> QueueMessage.builder()
				.queue(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
				.messageId(uuid) // Use user uuid as messageId, this ensure we do not get any duplicates in the queue
				.priority(1L)
				.dequeueTime(LocalDateTime.now().plusSeconds(3)) // Allow a few seconds before processing the message, so we are sure the changes to the assignments are persisted.
				.body(JsonSimpleMessage.toJson(createUserUpdateMessage(uuid)))
				.build())
			.toList();
		eventPublisher.publishEvent(BulkQueueMessage.builder()
			.messages(messages)
			.build());
	}

	private static UpdateUserAssignmentsMessage createUserUpdateMessage(final String userUuid) {
		return UpdateUserAssignmentsMessage.builder()
			.userUuid(userUuid)
			.timestamp(LocalDateTime.now())
			.build();
	}

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

	// this method exists solely so we can intercept it ;)
	public void flagUserDeleted(User user) {
		user.setDeleted(true);
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
	public void addRoleGroup(User user, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate, OrgUnit orgUnit, String caseNumber) {
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

		user.getRoleGroupAssignments().add(assignment);
	}

	@AuditLogIntercepted
	public void editRoleGroupAssignment(User user, UserRoleGroupAssignment roleGroupAssignment, LocalDate startDate, LocalDate stopDate, OrgUnit orgUnit, String caseNumber) {
		roleGroupAssignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		roleGroupAssignment.setStopDate(stopDate);
		roleGroupAssignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		roleGroupAssignment.setCaseNumber(caseNumber);

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
			UserUserRoleAssignment actualAssignment = assignment.get();
			if (actualAssignment.getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
					&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
					&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
				throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
			}

			if (!actualAssignment.getUserRole().isReadOnly()) {
				// ensure auditlogging like this
				self.removeUserRoleAssignment(user, actualAssignment);
				return true;
			}
		}

		return false;
	}

	// TODO: this method is only used by the ReadOnlyApi, and very likely noone has any use for
	//       this method, so deprecate it in future versions of the API
	public User getUserForReadOnlyApi(String id, Domain domain) {
		User user = userDao.findByUuidAndDeletedFalse(id).orElseGet(
				() -> userDao.findByUserIdAndDomainAndDeletedFalse(id, domain)
						.orElseThrow(() -> new NotFoundException("User with id '" + id + "' was not found in the database"))
		);

		return user;
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

	public void deleteDuplicateUserRoleAssignmentsOnUsers() {
		User admin = getByUserId(SecurityUtil.getUserId());
		if (admin == null) {
			throw new RuntimeException("No administrator logged in");
		}

		List<User> allUsers = getAll();
		List<User> dirtyUsers = new ArrayList<>();

		for (User user : allUsers) {
			Set<CurrentAssignment> allAssignments = assignmentService.getByUserIncludingInactive(user);
			if (allAssignments.isEmpty()) {
				continue;
			}

			// Find direct assignments
			List<CurrentAssignment> directAssignments = allAssignments.stream()
				.filter(a -> assignmentService.getAssignedThrough(a) == AssignedThrough.DIRECT)
				.collect(Collectors.toList());

			if (directAssignments.isEmpty()) {
				continue;
			}

			boolean dirty = false;

			// For each direct assignment, check if it's also assigned indirectly
			for (CurrentAssignment directAssignment : directAssignments) {
				long userRoleId = directAssignment.getUserRole().getId();

				// Check if this userRole also exists as an indirect assignment
				boolean hasIndirectAssignment = allAssignments.stream()
					.anyMatch(a -> assignmentService.getAssignedThrough(a) != AssignedThrough.DIRECT
						&& a.getUserRole().getId() == userRoleId);

				if (hasIndirectAssignment) {
					// Remove the direct assignment since it's redundant
					UserRole role = directAssignment.getUserRole();
					dirty = dirty | removeUserRole(user, role);
				}
			}

			if (dirty) {
				dirtyUsers.add(user);
			}
		}

		if (dirtyUsers.size() > 0) {
			auditLogger.log(admin, EventType.PERFORMED_USERROLE_CLEANUP);
			userDao.saveAll(dirtyUsers);
			
			queueMultipleForRecalculation(dirtyUsers.stream().map(u -> u.getUuid()).collect(Collectors.toSet()));
		}
	}

	public void deleteDuplicateRoleGroupAssignmentsOnUsers() {
		User admin = getByUserId(SecurityUtil.getUserId());
		if (admin == null) {
			throw new RuntimeException("No administrator logged in");
		}

		List<User> allUsers = getAll();
		List<User> dirtyUsers = new ArrayList<>();

		for (User user : allUsers) {
			Set<CurrentAssignment> uniqueRoleGroupAssignments = assignmentService.getUniqueRoleGroupAssignmentsByUser(user);

			if (uniqueRoleGroupAssignments.isEmpty()) {
				continue;
			}

			// Find direct assignments
			List<CurrentAssignment> directAssignments = uniqueRoleGroupAssignments.stream()
				.filter(a -> assignmentService.getAssignedThroughForRoleGroup(a) == AssignedThrough.DIRECT)
				.collect(Collectors.toList());

			if (directAssignments.isEmpty()) {
				continue;
			}

			boolean dirty = false;

			// For each direct assignment, check if it's also assigned indirectly
			for (CurrentAssignment directAssignment : directAssignments) {
				Long roleGroupId = directAssignment.getRoleGroup().getId();

				// Check if this roleGroup also exists as an indirect assignment
				boolean hasIndirectAssignment = uniqueRoleGroupAssignments.stream()
					.anyMatch(a -> assignmentService.getAssignedThroughForRoleGroup(a) != AssignedThrough.DIRECT
						&& a.getRoleGroup().getId() == roleGroupId);

				if (hasIndirectAssignment) {
					// Remove the direct assignment since it's redundant
					RoleGroup roleGroup = directAssignment.getRoleGroup();
					dirty = dirty | removeRoleGroup(user, roleGroup);
				}
			}

			if (dirty) {
				dirtyUsers.add(user);
			}
		}

		if (dirtyUsers.size() > 0) {
			auditLogger.log(admin, EventType.PERFORMED_ROLEGROUP_CLEANUP);
			userDao.saveAll(dirtyUsers);
			
			queueMultipleForRecalculation(dirtyUsers.stream().map(u -> u.getUuid()).collect(Collectors.toSet()));
		}
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

	public boolean isVikar(String userId) {
		String vikarRegEx = settingsService.getVikarRegEx();
		if (StringUtils.hasLength(userId) && StringUtils.hasLength(vikarRegEx)) {
			return userId.toLowerCase().matches(vikarRegEx);
		}

		return false;
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
					builder.append("<Constraint Name=\"" + constraint.getParameter() + "\">" + escapeXml(constraint.getValue()) + "</Constraint>");
				}
			}
			builder.append("</PrivilegeGroup>");
		}
		builder.append("</bpp:PrivilegeList>");

		return Base64.getEncoder().encodeToString(builder.toString().getBytes());
	}

	private String escapeXml(String value) {
	    if (value == null) {
	        return null;
	    }
	    return value.replace("&", "&amp;")
	                .replace("<", "&lt;")
	                .replace(">", "&gt;")
	                .replace("\"", "&quot;")
	                .replace("'", "&apos;");
	}

	public List<PrivilegeGroup> generateOIOBPPPrivileges(User user, List<ItSystem> itSystems, Map<String, String> roleMap) {
		List<PrivilegeGroup> result = new ArrayList<>();

		if (itSystems == null) {
			itSystems = itSystemService.getAll();
		}

		//filter out itsystems that are blocked
		itSystems = itSystems.stream().filter(its -> !its.isAccessBlocked()).collect(Collectors.toList());
		// should we create a PrivilegeGroup per systemrole or per userrole?
		boolean expandToBsr = shouldExpandToBsr(itSystems);

		final Set<CurrentAssignment> userAssignments = assignmentService.getByUserAndItSystems(user, itSystems);

		Set<String> addedIdentifiers = new HashSet<>();

		PrivilegeGroup privilegeGroup = new PrivilegeGroup(); // dummy assignment to make IDE happy
		for (CurrentAssignment assignment : userAssignments) {
			UserRole userRole = assignment.getUserRole();
			if (userRole == null) {
				continue; // this userrole no longer exists
			}

			// if the userRole allows postponing, then the constraints might (likely) differ on each assignment,
			// but for those direct/inherited, the constraints will be identical, so we can skip duplicates on those
			if (!userRole.isAllowPostponing()) {
				String identifier = userRole.getIdentifier();
				if (addedIdentifiers.contains(identifier)) {
					log.info("Removed duplicate " + userRole.getName() + " / " + userRole.getItSystem().getName() + " from " + user.getUserId());
					continue;
				}

				addedIdentifiers.add(identifier);
			}

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
							case SELECTED_INHERITED:
								if (constraint.getConstraintType().getEntityId().equals(Constants.KLE_CONSTRAINT_ENTITY_ID)) {
									constraintValue.append(getKLEConstraint(user, ConstraintValueType.SELECTED_INHERITED));
								}
								else if (constraint.getConstraintType().getEntityId().equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID) ||
										 constraint.getConstraintType().getEntityId().equals(Constants.OU_CONSTRAINT_ENTITY_ID)) {

									List<String> organisationConstraintUuids = organisationConstraintUtil.getOrganisationConstraintUuids(constraint.getConstraintValue());

									String csvFormat = String.join(",", organisationConstraintUuids);
									constraintValue.append(csvFormat);
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
								if (assignment.getPostponedConstraints() != null) {
									for (CurrentAssignmentPostponedConstraint postponedConstraint : assignment.getPostponedConstraints()) {
										if (constraint.getConstraintType() != null
												&& postponedConstraint.getConstraintTypeId() == constraint.getConstraintType().getId()
												&& postponedConstraint.getSystemRoleId() == systemRole.getSystemRole().getId()) {
											List<String> values = postponedConstraint.getValue();
											if (values != null) {
												constraintValue.append(String.join(",", values));
											}
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
			case SELECTED_INHERITED:
				throw new RuntimeException("SELECTED_INHERITED constraints should never call getKLEConstraint method");
			case INHERITED:
				kleSet.addAll(kleService.getKleAssignments(user, KleType.PERFORMING, true).stream().map(KleAssignment::getCode).collect(Collectors.toList()));
				break;
			case EXTENDED_INHERITED:
				kleSet.addAll(kleService.getKleAssignments(user, KleType.INTEREST, true).stream().map(KleAssignment::getCode).collect(Collectors.toList()));
				break;
			case READ_AND_WRITE:
				List<String> performings = kleService.getKleAssignments(user, KleType.PERFORMING, true).stream().map(KleAssignment::getCode).collect(Collectors.toList());
				List<String> interests = kleService.getKleAssignments(user, KleType.INTEREST, true).stream().map(KleAssignment::getCode).collect(Collectors.toList());

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

	public String getOrganisationConstraint(User user, ConstraintValueType constraintValueType) {
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
		List<User> usersFlat = jdbcTemplate.query(SELECT_THIN_USERS_SQL, (RowMapper<User>) (rs, _) -> {
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

	public boolean isManagerOrSubstituteManagerFor(User currentUser, User subject) {
		if (!SecurityUtil.hasRole(Constants.ROLE_MANAGER) && !SecurityUtil.hasRole(Constants.ROLE_SUBSTITUTE)) {
			return false;
		}
		return subject.getPositions().stream()
			.anyMatch(p -> managerSubstituteService.isManagerForOrgUnit(p.getOrgUnit()) || managerSubstituteService.isSubstituteforOrgUnit(currentUser, p.getOrgUnit()));
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

	@Transactional(readOnly = true)
	public Map<User, Set<CurrentAssignment>> getAllWithNemLoginAssignments(List<ItSystem> itSystems) {
		Map<User, Set<CurrentAssignment>> map = new HashMap<>();

		List<User> users = getAllWithNemLoginUuid();
		for (User user : users) {
			Set<CurrentAssignment> assignments = assignmentService.getByUserAndItSystemsWithRoleDetails(user, itSystems);
			map.put(user, assignments);
		}

		return map;
	}

	@Transactional
	public void cleanupDisabledUserAssignments() {
		log.debug("Starting cleanup of assignments for disabled users");

		try {
			// Get the configured number of days from settings (defaults to 180)
			Integer daysThreshold = settingsService.getRemoveDirectAssignmentsForDisabled();

			// null/blank = funktionen slået fra; 0 = ryd op straks (samme dag som brugeren bliver disabled/deleted)
			if (daysThreshold == null || daysThreshold < 0) {
				log.debug("Disabled user cleanup is disabled (days threshold: {})", daysThreshold);
				return;
			}

			// Calculate the cutoff date.
			// cutoffDate bruges til disabledAt (LocalDate-felt). cutoffDateExclusiveUpper er starten af dagen efter
			// cutoffDate og bruges som strict-less-than-grænse mod lastUpdated (Date-felt) for deleted-brugere,
			// så alt der er opdateret til-og-med cutoffDate fanges, uanset tidspunkt på dagen.
			LocalDate cutoffDate = LocalDate.now().minusDays(daysThreshold);
			Date cutoffDateExclusiveUpper = Date.from(cutoffDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
			log.debug("Looking for disabled/deleted users older than: {}", cutoffDate);

			// Find users who have been disabled or deleted for the specified number of days or more
			List<User> usersToCleanup = userDao.findDisabledUsersOlderThanWithAssignments(cutoffDate, cutoffDateExclusiveUpper);

			if (usersToCleanup.isEmpty()) {
				log.debug("No disabled users found older than cutoff date");
				return;
			}

			int totalUsersProcessed = 0;
			int totalUserRoleAssignmentsDeleted = 0;
			int totalRoleGroupAssignmentsDeleted = 0;

			for (User user : usersToCleanup) {
				log.debug("Processing disabled user: {} (disabled at: {})", user.getName(), user.getDisabledAt());

				// Count assignments before clearing
				int userRoleCount = user.getUserRoleAssignments() != null ? user.getUserRoleAssignments().size() : 0;
				int roleGroupCount = user.getRoleGroupAssignments() != null ? user.getRoleGroupAssignments().size() : 0;

				// Clear the collections
				if (userRoleCount > 0) {
					user.getUserRoleAssignments().clear();
					totalUserRoleAssignmentsDeleted += userRoleCount;
					log.debug("Cleared {} user role assignments for user: {}", userRoleCount, user.getName());
				}

				if (roleGroupCount > 0) {
					user.getRoleGroupAssignments().clear();
					totalRoleGroupAssignmentsDeleted += roleGroupCount;
					log.debug("Cleared {} role group assignments for user: {}", roleGroupCount, user.getName());
				}

				// Save the user to persist the changes
				if (userRoleCount > 0 || roleGroupCount > 0) {
					userDao.save(user);
				}

				totalUsersProcessed++;
			}

			log.debug("Cleanup completed. Processed {} users, deleted {} user role assignments and {} role group assignments",
				totalUsersProcessed, totalUserRoleAssignmentsDeleted, totalRoleGroupAssignmentsDeleted);

		} catch (Exception e) {
			log.error("Error during disabled user assignment cleanup", e);
		}
	}
}
