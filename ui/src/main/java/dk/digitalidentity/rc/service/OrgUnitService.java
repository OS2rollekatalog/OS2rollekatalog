package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.exceptions.OrgUnitNotFoundException;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.OrgUnitAssignedToUserRoleDTO;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole2;
import dk.digitalidentity.rc.service.model.RoleAssignedToOrgUnitDTO;
import dk.digitalidentity.rc.service.model.RoleWithDateDTO;
import dk.digitalidentity.rc.util.StreamExtensions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

@Slf4j
@Service
@EnableScheduling
@EnableCaching
public class OrgUnitService {

	@Autowired
	private OrgUnitDao orgUnitDao;

	@Autowired
	private UserDao userDao;

	@Autowired
	private RoleGroupDao roleGroupDao;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private PositionService positionService;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService self;

	@Autowired
	private TitleDao titleDao;

	@Autowired
	private OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;

	@Autowired
	private OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;

	@Autowired
	private DomainService domainService;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	@Autowired
	private SettingsService settingsService;

	@Value("${environment.dev:false}")
	private boolean devEnvironment;

	// dao methods

	public OrgUnit save(OrgUnit orgUnit) {
		// TODO: can be removed once all all API's have been updated to ensure this is set
		if (orgUnit.getLevel() == null) {
			orgUnit.setLevel(OrgUnitLevel.NONE);
		}

		return orgUnitDao.save(orgUnit);
	}

	public Iterable<OrgUnit> save(Iterable<OrgUnit> orgUnits) {
		// TODO: can be removed once all all API's have been updated to ensure this is set
		for (OrgUnit orgUnit : orgUnits) {
			if (orgUnit.getLevel() == null) {
				orgUnit.setLevel(OrgUnitLevel.NONE);
			}
		}

		return orgUnitDao.saveAll(orgUnits);
	}

	public OrgUnit getRoot() {
		return orgUnitDao.findByActiveTrueAndParentIsNull();
	}

	public long countAllWithRoleGroup(RoleGroup role) {
		return orgUnitDao.countByActiveTrueAndRoleGroupAssignmentsRoleGroup(role);
	}

	public long countAllWithRole(UserRole userRole) {
		return orgUnitDao.countByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
	}

	public OrgUnit getByUuid(String uuid) {
		OrgUnit ou = orgUnitDao.findByUuidAndActiveTrue(uuid);
		return !isActiveAndIncluded(ou) ? null : ou;
	}

	public List<OrgUnit> getByManagerMatchingUserAndSubstitute(User manager, User substitite) {
		List<OrgUnit> orgUnits = new ArrayList<>();

		for (ManagerSubstitute sub : manager.getManagerSubstitutes()) {
			if (Objects.equals(sub.getSubstitute().getUuid(), substitite.getUuid())) {
				orgUnits.add(sub.getOrgUnit());
			}
		}

		return orgUnits;
	}

	// used when we need direct match with manager, ignoring substitutes (e.g. during login)
	public List<OrgUnit> getByManagerMatchingUser(User user) {
		return orgUnitDao.findByManager(user);
	}

	public List<OrgUnit> getAllWithManager() {
		return orgUnitDao.findByActiveTrueAndManagerNotNull().stream().filter(this::isActiveAndIncluded).collect(Collectors.toList());
	}

	public List<OrgUnit> getByAuthorizationManagerMatchingUser(User user) {
		return orgUnitDao.findByAuthorizationManagersUser(user);
	}

	public List<OrgUnit> getActiveByAuthorizationManagerOrManagerMatchingUser(User user) {
		List<OrgUnit> orgUnits = orgUnitDao.findByAuthorizationManagersUser(user);
		List<OrgUnit> orgUnits2 = orgUnitDao.findByManager(user);

		List<User> managers = userService.getSubstitutesManager(user);
		for (User manager : managers) {
			List<OrgUnit> ous = orgUnitDao.findByManager(manager);
			orgUnits2.addAll(ous);
		}

		// ensure no duplicates
		Set<String> existingUuids = orgUnits.stream()
			.map(OrgUnit::getUuid)
			.collect(Collectors.toSet());
		for (OrgUnit ou : orgUnits2) {
			if (existingUuids.add(ou.getUuid())) { // add() returnerer false hvis den allerede findes
				orgUnits.add(ou);
			}
		}
		return orgUnits.stream().filter(this::isActiveAndIncluded).toList();
	}

	public Map<String, String> getManagerAndSubstituteEmail(OrgUnit orgUnit, boolean preferSubstitute) {
		Map<String, String> result = new HashMap<String, String>();

		if (orgUnit.getManager() != null) {
			for (User substitute : managerSubstituteService.getSubstitutesForOrgUnit(orgUnit)) {
				if (!substitute.isDeleted() && StringUtils.hasLength(substitute.getEmail())) {
					result.put(substitute.getEmail(), substitute.getName());
				}
			}

			if (!preferSubstitute || result.size() == 0) {
				if (StringUtils.hasLength(orgUnit.getManager().getEmail())) {
					result.put(orgUnit.getManager().getEmail(), orgUnit.getManager().getName());
				}
			}
		}

		return result;
	}

	// used when we need a list of orgUnits that the user is a manager for (or a substitute-manager for)
	public List<OrgUnit> getByManager() {
		List<OrgUnit> result = new ArrayList<>();

		String userId = SecurityUtil.getUserId();
		if (userId != null) {
			User user = userDao.findByUserIdAndDomainAndDeletedFalse(userId, domainService.getPrimaryDomain()).orElse(null);

			if (user != null) {
				if (SecurityUtil.hasRole(Constants.ROLE_MANAGER)) {
					result.addAll(getByManagerMatchingUser(user));
				}

				if (SecurityUtil.hasRole(Constants.ROLE_SUBSTITUTE)) {
					List<User> managers = securityUtil.getManagersBySubstitute();

					for (User manager : managers) {
						result.addAll(getByManagerMatchingUserAndSubstitute(manager, user));
					}
				}
			}
		}

		return result.stream().filter(ou -> isActiveAndIncluded(ou)).collect(Collectors.toList());
	}

	public List<OrgUnit> getAll() {
		return orgUnitDao.findByActiveTrue().stream().filter(o -> isActiveAndIncluded(o)).collect(Collectors.toList());
	}

	@SuppressWarnings("deprecation")
	public List<OrgUnit> getAllIncludingInactive() {
		return orgUnitDao.findAll();
	}

	@SuppressWarnings("deprecation")
	public List<OrgUnit> getAllWithRoleGroupIncludingInactive(RoleGroup role) {
		return orgUnitDao.findByRoleGroupAssignmentsRoleGroup(role);
	}

	@SuppressWarnings("deprecation")
	public List<OrgUnit> getAllWithRoleIncludingInactive(UserRole userRole) {
		return orgUnitDao.findByUserRoleAssignmentsUserRole(userRole);
	}

	@Cacheable(value = "orgUnits")
	public List<OrgUnit> getAllCached() {
		return orgUnitDao.findByActiveTrue().stream().filter(o -> isActiveAndIncluded(o)).collect(Collectors.toList());
	}

	@Cacheable(value = "orgUnitsIncludingExcluded")
	public List<OrgUnit> getAllCachedIncludingExcluded() {
		return orgUnitDao.findByActiveTrue();
	}

	@Caching(evict = {
			@CacheEvict(value = "orgUnits", allEntries = true),
			@CacheEvict(value = "orgUnitsIncludingExcluded", allEntries = true)
	})
	public void expireOrgUnitCache() {
		;
	}

	// clears cache every 5 minutes
	@Scheduled(fixedDelay = 5 * 60 * 1000)
	public void resetOrgUnitCache() {
		self.expireOrgUnitCache();
	}

	// TODO: the organisation importer does not use this method
	// utility methods
	@AuditLogIntercepted
	public boolean addKLE(OrgUnit ou, KleType assignmentType, String code) {
		boolean found = false;

		for (KLEMapping kleMapping : ou.getKles()) {
			if (kleMapping.getAssignmentType().equals(assignmentType) && kleMapping.getCode().equals(code)) {
				found = true;
				break;
			}
		}

		if (!found) {
			KLEMapping kle = new KLEMapping();
			kle.setOrgUnit(ou);
			kle.setCode(code);
			kle.setAssignmentType(assignmentType);

			ou.getKles().add(kle);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeKLE(OrgUnit ou, KleType assignmentType, String code) {
		KLEMapping existing = null;

		for (KLEMapping kleMapping : ou.getKles()) {
			if (kleMapping.getAssignmentType().equals(assignmentType) && kleMapping.getCode().equals(code)) {
				existing = kleMapping;
				break;
			}
		}

		if (existing != null) {
			ou.getKles().remove(existing);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public void addRoleGroup(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles, boolean negativeTitles) {
		String userFullname = SecurityUtil.getUserFullname();
		String userId = SecurityUtil.getUserId();
		OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
		assignment.setOrgUnit(ou);
		assignment.setInherit(inherit);
		assignment.setRoleGroup(roleGroup);
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
		assignment.setStopDateUser(userFullname + "(" + userId + ")");

		if (configuration.getTitles().isEnabled() && (titles != null && !titles.isEmpty())) {
			Collection<Title> titlesByUuid = CollectionUtils.emptyIfNull(titleDao.findByUuidInAndActiveTrue(titles));
			if(titlesByUuid.isEmpty()) {
				assignment.setContainsTitles(ContainsTitles.NO);
			}
			else if (negativeTitles) {
				assignment.setContainsTitles(ContainsTitles.NEGATIVE);
			}
			else {
				assignment.setContainsTitles(ContainsTitles.POSITIVE);
			}
			assignment.setTitles(new ArrayList<>(titlesByUuid));
		} else {
			Collection<User> usersById = CollectionUtils.emptyIfNull(userDao.findByUuidInAndDeletedFalse(exceptedUsers));
			assignment.setContainsExceptedUsers(!usersById.isEmpty());
			assignment.setExceptedUsers(new ArrayList<>(usersById));
		}

		ou.getRoleGroupAssignments().add(assignment);

		assignment.setAssignedByName(userFullname);
		assignment.setAssignedByUserId(userId);
		assignment.setAssignedTimestamp(new Date());
	}

	public void addRoleGroup(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles) {
		addRoleGroup(ou, roleGroup, inherit, startDate, stopDate, exceptedUsers, titles, false);
	}

	@AuditLogIntercepted
	public boolean updateRoleGroupAssignment(OrgUnit ou, OrgUnitRoleGroupAssignment assignment, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titleUuids, boolean negativeTitles) {
		boolean modified = false;

		if (!Objects.equals(assignment.getStartDate(), startDate) || !Objects.equals(assignment.getStopDate(), stopDate)) {
			// update timestamps if needed
			assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			assignment.setStopDate(stopDate);
			assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

			String userId = SecurityUtil.getUserId();
			assignment.setStopDateUser(userId);

			modified = true;
		}

		// Save changes to excepted users if there has been any change
		Collection<User> usersById = (exceptedUsers != null && !exceptedUsers.isEmpty()) ? (CollectionUtils.emptyIfNull(userDao.findByUuidInAndDeletedFalse(exceptedUsers))) : Collections.emptyList();
		if (usersById.isEmpty()) {
			assignment.setContainsExceptedUsers(false);
			assignment.setExceptedUsers(new ArrayList<>());
			modified = true;
		} else {
			Set<String> toBeSaved = usersById.stream().map(User::getUuid).collect(Collectors.toSet());
			Set<String> alreadySaved = CollectionUtils.emptyIfNull(assignment.getExceptedUsers()).stream().map(User::getUuid).collect(Collectors.toSet());

			Collection<String> intersection = CollectionUtils.intersection(toBeSaved, alreadySaved);
			if (intersection.size() != toBeSaved.size() || intersection.size() != alreadySaved.size()) {
				assignment.setContainsExceptedUsers(true);
				assignment.setExceptedUsers(new ArrayList<>(usersById));
				assignment.setInherit(false);
				modified = true;
			}
		}

		// only set titles if no excepted users
		if (!assignment.isContainsExceptedUsers() && titleUuids != null && !titleUuids.isEmpty()) {
			// Remove
			for (Iterator<Title> iterator = assignment.getTitles().iterator(); iterator.hasNext();) {
				Title title = iterator.next();
				if (!titleUuids.contains(title.getUuid())) {
					iterator.remove();
					modified = true;
				}
			}
			// Add
			List<Title> selectedTitles = titleDao.findByUuidInAndActiveTrue(titleUuids);
			for (Title title : selectedTitles) {
				if (!assignment.getTitles().contains(title)) {
					assignment.getTitles().add(title);
					modified = true;
				}
			}

			if (assignment.getTitles().isEmpty()) {
				if (!assignment.getContainsTitles().equals(ContainsTitles.NO)) {
					assignment.setContainsTitles(ContainsTitles.NO);
					modified = true;
				}
			} else if (negativeTitles) {
				if (!assignment.getContainsTitles().equals(ContainsTitles.NEGATIVE)) {
					assignment.setContainsTitles(ContainsTitles.NEGATIVE);
					modified = true;
				}
			} else {
				if (!assignment.getContainsTitles().equals(ContainsTitles.POSITIVE)) {
					assignment.setContainsTitles(ContainsTitles.POSITIVE);
					modified = true;
				}
			}
		} else {
			assignment.setContainsTitles(ContainsTitles.NO);
			assignment.setTitles(new ArrayList<>());
			modified = true;
		}

		// inherit is only possible if no excepted users
		if (!assignment.isContainsExceptedUsers() && assignment.isInherit() != inherit) {
			assignment.setInherit(inherit);
			modified = true;
		}

		if (modified) {
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
		}

		return modified;
	}

	public boolean updateRoleGroupAssignment(OrgUnit ou, OrgUnitRoleGroupAssignment assignment, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titleUuids) {
		return updateRoleGroupAssignment(ou, assignment, inherit, startDate, stopDate, exceptedUsers, titleUuids, false);
	}

	@AuditLogIntercepted
	public boolean removeRoleGroup(OrgUnit ou, RoleGroup roleGroup) {
		for (Iterator<OrgUnitRoleGroupAssignment> iterator = ou.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
			OrgUnitRoleGroupAssignment mapping = iterator.next();

			if (mapping.getRoleGroup().getId() == roleGroup.getId()) {
				iterator.remove();
				return true;
			}
		}

		return false;
	}

	@AuditLogIntercepted
	public void addUserRole(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles, boolean negativeTitles) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setOrgUnit(ou);
		assignment.setInherit(inherit);
		assignment.setUserRole(userRole);
		assignment.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
		assignment.setStopDate(stopDate);
		assignment.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

		if (configuration.getTitles().isEnabled() && (titles != null && !titles.isEmpty())) {
			Collection<Title> titlesByUuid = CollectionUtils.emptyIfNull(titleDao.findByUuidInAndActiveTrue(titles));
			if(negativeTitles) {
				assignment.setContainsTitles(ContainsTitles.NEGATIVE);
			}
			else {
				assignment.setContainsTitles(ContainsTitles.POSITIVE);
			}
			assignment.setTitles(new ArrayList<>(titlesByUuid));
		}
		else {
			assignment.setContainsTitles(ContainsTitles.NO);
			Collection<User> usersById = CollectionUtils.emptyIfNull(userDao.findByUuidInAndDeletedFalse(exceptedUsers));
			assignment.setContainsExceptedUsers(!usersById.isEmpty());
			assignment.setExceptedUsers(new ArrayList<>(usersById));
		}

		ou.getUserRoleAssignments().add(assignment);

		String userFullname = SecurityUtil.getUserFullname();
		String userId = SecurityUtil.getUserId();
		assignment.setAssignedByName(userFullname);
		assignment.setAssignedByUserId(userId);
		assignment.setAssignedTimestamp(new Date());

		assignment.setStopDateUser(userId);
	}

	//Method overloading to avoid breaking code base
	public void addUserRole(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles) {
		addUserRole(ou, userRole, inherit, startDate, stopDate, exceptedUsers, titles, false);
	}

	@AuditLogIntercepted
	public boolean updateUserRoleAssignment(OrgUnit ou, OrgUnitUserRoleAssignment assignment, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titleUuids, boolean negativeTitles) {
		boolean modified = false;

		if (!Objects.equals(assignment.getStartDate(), startDate) || !Objects.equals(assignment.getStopDate(), stopDate)) {
			// update timestamps if needed
			assignment.setStartDate(startDate);
			assignment.setStopDate(stopDate);
			assignment.setInactive(startDate != null && startDate.isAfter(LocalDate.now()));

			String userId = SecurityUtil.getUserId();
			assignment.setStopDateUser(userId);

			modified = true;
		}

		// Save changes to excepted users if there has been any change
		Collection<User> usersById = (exceptedUsers != null && !exceptedUsers.isEmpty()) ? (CollectionUtils.emptyIfNull(userDao.findByUuidInAndDeletedFalse(exceptedUsers))) : Collections.emptyList();
		if (usersById.isEmpty()) {
			assignment.setContainsExceptedUsers(false);
			assignment.setExceptedUsers(new ArrayList<>());
			modified = true;
		} else {
			Set<String> toBeSaved = usersById.stream().map(User::getUuid).collect(Collectors.toSet());
			Set<String> alreadySaved = CollectionUtils.emptyIfNull(assignment.getExceptedUsers()).stream().map(User::getUuid).collect(Collectors.toSet());

			Collection<String> intersection = CollectionUtils.intersection(toBeSaved, alreadySaved);
			if (intersection.size() != toBeSaved.size() || intersection.size() != alreadySaved.size()) {
				assignment.setContainsExceptedUsers(true);
				assignment.setExceptedUsers(new ArrayList<>(usersById));
				assignment.setInherit(false);
				modified = true;
			}
		}

		// only set titles if no excepted users
		if (!assignment.isContainsExceptedUsers() && titleUuids != null && !titleUuids.isEmpty()) {
			// Remove
			for (Iterator<Title> iterator = assignment.getTitles().iterator(); iterator.hasNext();) {
				Title title = iterator.next();
				if (!titleUuids.contains(title.getUuid())) {
					iterator.remove();
					modified = true;
				}
			}
			// Add
			List<Title> selectedTitles = titleDao.findByUuidInAndActiveTrue(titleUuids);
			for (Title title : selectedTitles) {
				if (!assignment.getTitles().contains(title)) {
					assignment.getTitles().add(title);
					modified = true;
				}
			}

			if (assignment.getTitles().isEmpty()) {
				if (!assignment.getContainsTitles().equals(ContainsTitles.NO)) {
					assignment.setContainsTitles(ContainsTitles.NO);
					modified = true;
				}
			} else if (negativeTitles) {
				if (!assignment.getContainsTitles().equals(ContainsTitles.NEGATIVE)) {
					assignment.setContainsTitles(ContainsTitles.NEGATIVE);
					modified = true;
				}
			} else {
				if (!assignment.getContainsTitles().equals(ContainsTitles.POSITIVE)) {
					assignment.setContainsTitles(ContainsTitles.POSITIVE);
					modified = true;
				}
			}
		} else {
			assignment.setContainsTitles(ContainsTitles.NO);
			assignment.setTitles(new ArrayList<>());
			modified = true;
		}

		// inherit is only possible if no excepted users
		if (!assignment.isContainsExceptedUsers() && assignment.isInherit() != inherit) {
			assignment.setInherit(inherit);
			modified = true;
		}

		if (modified) {
			assignment.setAssignedByName(SecurityUtil.getUserFullname());
			assignment.setAssignedByUserId(SecurityUtil.getUserId());
			assignment.setAssignedTimestamp(new Date());
		}

		return modified;
	}

	//Method overloading to avoid breaking code base
	public boolean updateUserRoleAssignment(OrgUnit ou, OrgUnitUserRoleAssignment assignment, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titleUuids) {
		return updateUserRoleAssignment(ou, assignment, inherit, startDate, stopDate, exceptedUsers, titleUuids, false);
	}

	@AuditLogIntercepted
	public boolean removeUserRole(OrgUnit ou, UserRole userRole) {
		for (Iterator<OrgUnitUserRoleAssignment> iterator = ou.getUserRoleAssignments().iterator(); iterator.hasNext();) {
			OrgUnitUserRoleAssignment mapping = iterator.next();

			if (mapping.getUserRole().getId() == userRole.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(mapping.getStopDateUser());
				iterator.remove();
				return true;
			}
		}

		return false;
	}

	@AuditLogIntercepted
	public void removeUserRoleAssignment(OrgUnit orgUnit, OrgUnitUserRoleAssignment assignment) {
		for (Iterator<OrgUnitUserRoleAssignment> iterator = orgUnit.getUserRoleAssignments().iterator(); iterator.hasNext();) {
			OrgUnitUserRoleAssignment a = iterator.next();

			if (assignment.getId() == a.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
				iterator.remove();
				break;
			}
		}
	}

	public boolean removeUserRoleAssignment(OrgUnit ou, long assignmentId) {
		Optional<OrgUnitUserRoleAssignment> assignment = ou.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignmentId).findAny();

		if (assignment.isPresent()) {
			if (assignment.get().getUserRole().getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)
					&& !SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)
					&& !SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM)) {
				throw new SecurityException("Kun administratorer kan fjerne Rollekatalog roller");
			}

			// trick to ensure auditlogging
			self.removeUserRoleAssignment(ou, assignment.get());

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public void removeRoleGroupAssignment(OrgUnit orgUnit, OrgUnitRoleGroupAssignment assignment) {
		for (Iterator<OrgUnitRoleGroupAssignment> iterator = orgUnit.getRoleGroupAssignments().iterator(); iterator.hasNext();) {
			OrgUnitRoleGroupAssignment a = iterator.next();

			if (assignment.getId() == a.getId()) {
				AuditLogContextHolder.getContext().setStopDateUserId(assignment.getStopDateUser());
				iterator.remove();
				break;
			}
		}
	}

	public boolean removeRoleGroupAssignment(OrgUnit ou, long assignmentId) {
		Optional<OrgUnitRoleGroupAssignment> assignment = ou.getRoleGroupAssignments().stream().filter(rga -> rga.getId() == assignmentId).findAny();
		if (assignment.isPresent()) {

			// trick to ensure auditlogging
			self.removeRoleGroupAssignment(ou, assignment.get());

			return true;
		}

		return false;
	}

	public List<UserRole> getUserRoles(String uuid, boolean inherit) throws OrgUnitNotFoundException {
		OrgUnit orgUnit = orgUnitDao.findByUuidAndActiveTrue(uuid);
		if (orgUnit != null && !isActiveAndIncluded(orgUnit)) {
			orgUnit = null;
		}

		if (orgUnit == null) {
			throw new OrgUnitNotFoundException("OrgUnit with id '" + uuid + "' was not found in the database");
		}

		return getUserRoles(orgUnit, inherit);
	}

	public List<Title> getTitles(OrgUnit orgUnit) {
		return positionService.findByOrgUnit(orgUnit)
				.stream()
// ROL-117, they want to see all titles, including inactive ones
				.filter(p -> p.getTitle() != null) // && p.getUser().isActive())
				.map(p -> p.getTitle())
				.filter(StreamExtensions.distinctByKey(t -> t.getUuid()))
				.collect(Collectors.toList());
	}

	public List<Title> getContainsTitlesForOrgUnit(OrgUnit orgUnit, ContainsTitles containsTitles) {
		return orgUnitUserRoleAssignmentDao.findByOrgUnitAndContainsTitles(orgUnit, containsTitles)
				.stream()
				.filter(orgUnitUserRoleAssignment -> !orgUnitUserRoleAssignment.getTitles().isEmpty())
				.flatMap(orgUnitUserRoleAssignment -> orgUnitUserRoleAssignment.getTitles().stream())
				.filter(StreamExtensions.distinctByKey(Title::getUuid))
				.toList();
	}

	public List<Title> getContainsTitlesAndInheritanceForOrgUnit(OrgUnit orgUnit, ContainsTitles containsTitles, boolean inheritance) {
		return orgUnitUserRoleAssignmentDao.findByOrgUnitAndContainsTitlesAndInherit(orgUnit, containsTitles, inheritance)
				.stream()
				.filter(orgUnitUserRoleAssignment -> !orgUnitUserRoleAssignment.getTitles().isEmpty())
				.flatMap(orgUnitUserRoleAssignment -> orgUnitUserRoleAssignment.getTitles().stream())
				.filter(StreamExtensions.distinctByKey(Title::getUuid))
				.toList();
	}

	public List<UserRole> getUserRoles(OrgUnit orgUnit, boolean inherit) {
		return getUserRolesWithUserFilter(orgUnit, inherit, null);
	}

	public List<UserRole> getUserRolesWithUserFilter(OrgUnit orgUnit, boolean inherit, User user) {
		if (inherit) {
			Set<UserRole> resultSet = new HashSet<>();

			getUserRolesRecursive(resultSet, orgUnit, false, user);

			return new ArrayList<>(resultSet);
		}

		List<UserRole> userRoles = new ArrayList<>();
		if (configuration.getTitles().isEnabled()) {
			userRoles = orgUnit.getUserRoleAssignments().stream().map(r -> r.getUserRole()).collect(Collectors.toList());
		}
		else {
			userRoles = orgUnit.getUserRoleAssignments().stream().filter(r -> r.getContainsTitles() == ContainsTitles.NO).map(r -> r.getUserRole()).collect(Collectors.toList());
		}

		return userRoles;
	}

	/**
	 * Copy of getUserRolesWithUserFilter used in new manage UI
	 */
	public List<RoleAssignedToOrgUnitDTO> getAllUserRolesAssignedToOrgUnit(OrgUnit orgUnit) {
		Set<RoleAssignedToOrgUnitDTO> resultSet = new HashSet<>();

		getUserRoleAssignmentsRecursive(resultSet, orgUnit, false);

		return new ArrayList<>(resultSet);
	}

	/**
	 * Copy of getUserRolesRecursive used in new manage UI
	 */
	private void getUserRoleAssignmentsRecursive(Set<RoleAssignedToOrgUnitDTO> resultSet, OrgUnit orgUnit, boolean inheritOnly) {
		if (inheritOnly) {
			// inherited
			List<RoleAssignedToOrgUnitDTO> userRoleAssignments = orgUnit.getUserRoleAssignments().stream().filter(OrgUnitUserRoleAssignment::isInherit).map(RoleAssignedToOrgUnitDTO::fromUserRoleAssignmentIndirect).collect(Collectors.toList());

			for (RoleAssignedToOrgUnitDTO userRoleAssignment : userRoleAssignments) {
			}
			resultSet.addAll(userRoleAssignments);
		}
		else {
			//Directly assigned
			List<RoleAssignedToOrgUnitDTO> userRoleAssignments = null;

			if (configuration.getTitles().isEnabled()) {
				userRoleAssignments = orgUnit.getUserRoleAssignments().stream().map(RoleAssignedToOrgUnitDTO::fromUserRoleAssignment).collect(Collectors.toList());
			}
			else {
				userRoleAssignments = orgUnit.getUserRoleAssignments().stream().filter(r -> r.getContainsTitles() == ContainsTitles.NO).map(RoleAssignedToOrgUnitDTO::fromUserRoleAssignment).collect(Collectors.toList());
			}

			resultSet.addAll(userRoleAssignments);
		}

		if (orgUnit.getParent() != null) {
			getUserRoleAssignmentsRecursive(resultSet, orgUnit.getParent(), true);
		}
	}

	// TODO: deprecate (do we have interceptors on this?)
	public List<OrgUnitUserRoleAssignment> getRoleMappings(OrgUnit orgUnit) {
		return orgUnit.getUserRoleAssignments();
	}

	// TODO: deprecate (do we have interceptors on this?)
	public List<OrgUnitRoleGroupAssignment> getRoleGroupMappings(OrgUnit orgUnit) {
		return orgUnit.getRoleGroupAssignments();
	}

	private void getUserRolesRecursive(Set<UserRole> resultSet, OrgUnit orgUnit, boolean inheritOnly, User user) {
		if (inheritOnly) {
			List<UserRole> userRoles = orgUnit.getUserRoleAssignments().stream().filter(r -> r.isInherit()).map(r -> r.getUserRole()).collect(Collectors.toList());

			resultSet.addAll(userRoles);
		}
		else {

			List<UserRole> userRoles = new ArrayList<>();
			if (configuration.getTitles().isEnabled()) {
				userRoles = orgUnit.getUserRoleAssignments().stream().map(r -> r.getUserRole()).collect(Collectors.toList());
			}
			else {
				userRoles = orgUnit.getUserRoleAssignments().stream().filter(r -> r.getContainsTitles() == ContainsTitles.NO).map(r -> r.getUserRole()).collect(Collectors.toList());
			}

			resultSet.addAll(userRoles);
		}

		if (orgUnit.getParent() != null) {
			getUserRolesRecursive(resultSet, orgUnit.getParent(), true, user);
		}
	}

	public List<RoleGroup> getRoleGroups(String uuid, boolean inherit) throws OrgUnitNotFoundException {
		OrgUnit orgUnit = orgUnitDao.findByUuidAndActiveTrue(uuid);
		if (orgUnit != null && !isActiveAndIncluded(orgUnit)) {
			orgUnit = null;
		}
		if (orgUnit == null) {
			throw new OrgUnitNotFoundException("OrgUnit with id '" + uuid + "' was not found in the database");
		}

		return getRoleGroups(orgUnit, inherit);
	}

	public List<RoleGroup> getRoleGroups(OrgUnit orgUnit, boolean inherit) {
		return getRoleGroupsWithUserFilter(orgUnit, inherit, null);
	}

	/**
	 * Copy of getRoleGroupsWithUserFilter used in new manage UI
	 */
	public List<RoleAssignedToOrgUnitDTO> getAllRoleGroupsAssignedToOrgUnit(OrgUnit orgUnit) {
		Set<RoleAssignedToOrgUnitDTO> resultSet = new HashSet<>();

		getUserRoleGroupAssignmentsRecursive(resultSet, orgUnit, false);

		return new ArrayList<>(resultSet);
	}

	/**
	 * Copy of getUserRoleGroupsRecursive used in new manage UI
	 */
	private void getUserRoleGroupAssignmentsRecursive(Set<RoleAssignedToOrgUnitDTO> resultSet, OrgUnit orgUnit, boolean inheritOnly) {
		if (inheritOnly) {
			//Inherited
			List<RoleAssignedToOrgUnitDTO> roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(OrgUnitRoleGroupAssignment::isInherit).map(RoleAssignedToOrgUnitDTO::fromRoleGroupAssignmentIndirect).collect(Collectors.toList());

			resultSet.addAll(roleGroupAssignments);
		}
		else {
			//Direct
			List<RoleAssignedToOrgUnitDTO> roleGroupAssignments = null;

			if (configuration.getTitles().isEnabled()) {
				roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().map(RoleAssignedToOrgUnitDTO::fromRoleGroupAssignment).collect(Collectors.toList());
			} else {
				roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(r -> r.getContainsTitles() == ContainsTitles.NO).map(RoleAssignedToOrgUnitDTO::fromRoleGroupAssignment).collect(Collectors.toList());
			}

			resultSet.addAll(roleGroupAssignments);
		}

		if (orgUnit.getParent() != null) {
			getUserRoleGroupAssignmentsRecursive(resultSet, orgUnit.getParent(), true);
		}
	}

	public List<RoleGroup> getRoleGroupsWithUserFilter(OrgUnit orgUnit, boolean inherit, User user) {
		if (inherit) {
			Set<RoleGroup> resultSet = new HashSet<>();

			getUserRoleGroupsRecursive(resultSet, orgUnit, user, false);

			return new ArrayList<>(resultSet);
		}

		List<RoleGroup> roleGroups = null;
		if (configuration.getTitles().isEnabled()) {
			roleGroups = orgUnit.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());
		}
		else {
			roleGroups = orgUnit.getRoleGroupAssignments().stream().filter(r -> r.getContainsTitles() == ContainsTitles.NO).map(r -> r.getRoleGroup()).collect(Collectors.toList());
		}

		return roleGroups;
	}

	public List<RoleWithDateDTO> getNotInheritedRoleGroupsWithDate(OrgUnit orgUnit) {
		List<OrgUnitRoleGroupAssignment> roleGroupAssignments = null;

		if (configuration.getTitles().isEnabled()) {
			roleGroupAssignments = orgUnit.getRoleGroupAssignments();
		} else {
			roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(rga -> rga.getContainsTitles() == ContainsTitles.NO)
					.collect(Collectors.toList());
		}
		return roleGroupAssignments.stream().map(r -> RoleWithDateDTO.builder()
				.id(r.getRoleGroup().getId())
				.startDate(r.getStartDate())
				.stopDate(r.getStopDate()).build())
		.collect(Collectors.toList());
	}

	public List<RoleWithDateDTO> getNotInheritedUserRolesWithDate(OrgUnit orgUnit) {
		List<OrgUnitUserRoleAssignment> userRoleAssignments = orgUnit.getUserRoleAssignments();

		if (configuration.getTitles().isEnabled()) {
			userRoleAssignments = orgUnit.getUserRoleAssignments();
		} else {
			userRoleAssignments = orgUnit.getUserRoleAssignments().stream().filter(ura -> ura.getContainsTitles() == ContainsTitles.NO)
					.collect(Collectors.toList());
		}

		return userRoleAssignments.stream().map(r -> RoleWithDateDTO.builder()
				.id(r.getUserRole().getId())
				.startDate(r.getStartDate())
				.stopDate(r.getStopDate()).build())
		.collect(Collectors.toList());
	}

	private void getUserRoleGroupsRecursive(Set<RoleGroup> resultSet, OrgUnit orgUnit, User user, boolean inheritOnly) {
		if (inheritOnly) {
			List<RoleGroup> roleGroups = orgUnit.getRoleGroupAssignments().stream().filter(r -> r.isInherit()).map(r -> r.getRoleGroup()).collect(Collectors.toList());

			resultSet.addAll(roleGroups);
		}
		else {

			List<RoleGroup> roleGroups = null;
			if (configuration.getTitles().isEnabled()) {
				roleGroups = orgUnit.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());
			} else {
				roleGroups = orgUnit.getRoleGroupAssignments().stream().filter(r -> r.getContainsTitles() == ContainsTitles.NO).map(r -> r.getRoleGroup()).collect(Collectors.toList());
			}

			resultSet.addAll(roleGroups);
		}

		if (orgUnit.getParent() != null) {
			getUserRoleGroupsRecursive(resultSet, orgUnit.getParent(), user, true);
		}
	}

	@Deprecated
	public List<OrgUnit> getByRoleGroup(RoleGroup roleGroup) {
		return orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
	}

	public List<OrgUnit> getByRoleGroup(RoleGroup roleGroup, boolean inactive) {
		return orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, inactive)
				.stream().filter(this::isActiveAndIncluded).collect(Collectors.toList());
	}

	@Deprecated
	public List<OrgUnit> getByUserRole(UserRole userRole) {
		return orgUnitDao.findByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
	}

	public List<OrgUnit> getByUserRole(UserRole userRole, boolean inactive) {
		return orgUnitDao.findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, inactive)
				.stream().filter(o -> isActiveAndIncluded(o)).collect(Collectors.toList());
	}

	public List<User> getManagers() {
		return userDao.getManagers();
	}

	public User getManager(OrgUnit orgUnit) {
		if (orgUnit.getManager() != null) {
			return orgUnit.getManager();
		}

		if (orgUnit.getParent() != null) {
			return getManager(orgUnit.getParent());
		}

		return null;
	}

	public List<OrgUnitWithRole2> getActiveOrgUnitsWithRoleGroup(RoleGroup roleGroup) {
		List<OrgUnitWithRole2> result = new ArrayList<>();

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		for (OrgUnitRoleGroupAssignment assignment : orgUnitRoleGroupAssignmentDao.findByRoleGroup(roleGroup)) {
			if (!isActiveAndIncluded(assignment.getOrgUnit())) {
				continue;
			}
			OrgUnitWithRole2 mapping = new OrgUnitWithRole2();
			mapping.setOuName(assignment.getOrgUnit().getName());
			mapping.setOuUuid(assignment.getOrgUnit().getUuid());
			mapping.setAssignment(RoleAssignedToOrgUnitDTO.fromRoleGroupAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);
		}

		return result;
	}

	public List<OrgUnitWithRole2> getActiveOrgUnitsWithUserRole(UserRole userRole) {
		List<OrgUnitWithRole2> result = new ArrayList<>();

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		for (OrgUnitUserRoleAssignment assignment : orgUnitUserRoleAssignmentDao.findByUserRole(userRole)) {
			if (!isActiveAndIncluded(assignment.getOrgUnit())) {
				continue;
			}
			OrgUnitWithRole2 mapping = new OrgUnitWithRole2();
			mapping.setOuName(assignment.getOrgUnit().getName());
			mapping.setOuUuid(assignment.getOrgUnit().getUuid());
			mapping.setAssignment(RoleAssignedToOrgUnitDTO.fromUserRoleAssignment(assignment));
			mapping.getAssignment().setCanEdit(canEdit);

			result.add(mapping);
		}

		return result;
	}

	// TODO: does not handle titles yet
	public List<OrgUnitWithRole> getOrgUnitsWithUserRole(UserRole userRole, boolean findIndirectlyAssignedRoles) {
		List<OrgUnitWithRole> result = new ArrayList<>();
		List<OrgUnit> ousNoInherit = orgUnitDao.findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(userRole, false, false)
				.stream().filter(this::isActiveAndIncluded).collect(Collectors.toList());
		for (OrgUnit orgUnit : ousNoInherit) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}

		List<OrgUnit> ousInherit = orgUnitDao.findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(userRole, true, false)
				.stream().filter(this::isActiveAndIncluded).collect(Collectors.toList());
		for (OrgUnit orgUnit : ousInherit) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);

			if (findIndirectlyAssignedRoles) {
				for (OrgUnit child : orgUnit.getChildren()) {
					getOrgUnitsRecursive(child, result);
				}
			}
		}

		// For all roleGroups that have selected UserRole
		for (RoleGroup roleGroup : roleGroupDao.findByUserRoleAssignmentsUserRole(userRole)) {
			// Get all orgUnits that have this roleGroup assigned directly
			for (OrgUnit orgUnit : orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, false, false).stream().filter(this::isActiveAndIncluded).collect(Collectors.toList())) {
				OrgUnitWithRole mapping = new OrgUnitWithRole();
				mapping.setOrgUnit(orgUnit);
				mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);

				result.add(mapping);
			}

			if (findIndirectlyAssignedRoles) {
				// Get all orgUnits that inherit that RoleGroup
				for (OrgUnit orgUnit : orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, true, false).stream().filter(this::isActiveAndIncluded).collect(Collectors.toList())) {
					OrgUnitWithRole mapping = new OrgUnitWithRole();
					mapping.setOrgUnit(orgUnit);
					mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);

					result.add(mapping);

					if (findIndirectlyAssignedRoles) {
						for (OrgUnit child : orgUnit.getChildren()) {
							getOrgUnitsRecursive(child, result);
						}
					}
				}
			}
		}

		return result;
	}

	public List<OrgUnitAssignedToUserRoleDTO> getOrgUnitAssignmentsWithUserRoleDirectlyAssigned(UserRole userRole) {
		List<OrgUnitAssignedToUserRoleDTO> result = new ArrayList<>();

		List<OrgUnit> orgUnitsWithRole = orgUnitDao.findByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, false).stream().filter(o -> isActiveAndIncluded(o)).distinct().collect(Collectors.toList());
		for (OrgUnit orgUnit : orgUnitsWithRole) {
			List<OrgUnitUserRoleAssignment> userRoleAssignments = orgUnit.getUserRoleAssignments().stream().filter(a -> a.getUserRole().equals(userRole)).collect(Collectors.toList());
			for (OrgUnitUserRoleAssignment userUserRoleAssignment : userRoleAssignments) {
				OrgUnitAssignedToUserRoleDTO mapping = new OrgUnitAssignedToUserRoleDTO();
				mapping.setOrgUnit(orgUnit);
				mapping.setAssignedThrough(AssignedThrough.DIRECT);
				mapping.setAssignmentId(userUserRoleAssignment.getId());
				mapping.setCanEdit(false);
				mapping.setStartDate(userUserRoleAssignment.getStartDate());
				mapping.setStopDate(userUserRoleAssignment.getStopDate());
				if (userUserRoleAssignment.getContainsTitles() != ContainsTitles.NO) {
					mapping.setAssignmentType(userUserRoleAssignment.getTitles().size());// assigned through titles
				} else if (userUserRoleAssignment.isContainsExceptedUsers()) {
					mapping.setAssignmentType(0);// Assigned to all with exceptions
				} else {
					mapping.setAssignmentType(userUserRoleAssignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
				}

				result.add(mapping);
			}
		}

		// For all roleGroups that have selected UserRole
		for (RoleGroup roleGroup : roleGroupDao.findByUserRoleAssignmentsUserRole(userRole)) {
			// Get all orgUnits that have this roleGroup assigned directly
			List<OrgUnit> orgUnitsWithRoleGroup = orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false)
					.stream().filter(this::isActiveAndIncluded).distinct().collect(Collectors.toList());
			for (OrgUnit orgUnit : orgUnitsWithRoleGroup) {
				List<OrgUnitRoleGroupAssignment> userRoleAssignments = orgUnit.getRoleGroupAssignments().stream().filter(a -> a.getRoleGroup().equals(roleGroup)).collect(Collectors.toList());
				for (OrgUnitRoleGroupAssignment userUserRoleAssignment : userRoleAssignments) {
					OrgUnitAssignedToUserRoleDTO mapping = new OrgUnitAssignedToUserRoleDTO();
					mapping.setOrgUnit(orgUnit);
					mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);
					mapping.setAssignmentId(userUserRoleAssignment.getId());
					mapping.setRoleId(userUserRoleAssignment.getRoleGroup().getId());
					mapping.setCanEdit(false);
					mapping.setStartDate(userUserRoleAssignment.getStartDate());
					mapping.setStopDate(userUserRoleAssignment.getStopDate());
					if (userUserRoleAssignment.getContainsTitles() != ContainsTitles.NO) {
						mapping.setAssignmentType(userUserRoleAssignment.getTitles().size());// assigned through titles
					} else if (userUserRoleAssignment.isContainsExceptedUsers()) {
						mapping.setAssignmentType(0);// Assigned to all with exceptions
					} else {
						mapping.setAssignmentType(userUserRoleAssignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
					}

					result.add(mapping);
				}
			}
		}

		return result;
	}

	public List<OrgUnitAssignedToUserRoleDTO> getOrgUnitAssignmentsWithRoleGroupDirectlyAssigned(RoleGroup roleGroup) {
		List<OrgUnitAssignedToUserRoleDTO> result = new ArrayList<>();

		List<OrgUnit> orgUnitsWithRole = orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, false).stream().filter(this::isActiveAndIncluded).distinct().collect(Collectors.toList());
		for (OrgUnit orgUnit : orgUnitsWithRole) {
			List<OrgUnitRoleGroupAssignment> roleGroupAssignments = orgUnit.getRoleGroupAssignments().stream().filter(a -> a.getRoleGroup().equals(roleGroup)).collect(Collectors.toList());
			for (OrgUnitRoleGroupAssignment userUserRoleAssignment : roleGroupAssignments) {
				OrgUnitAssignedToUserRoleDTO mapping = new OrgUnitAssignedToUserRoleDTO();
				mapping.setOrgUnit(orgUnit);
				mapping.setAssignedThrough(AssignedThrough.DIRECT);
				mapping.setAssignmentId(userUserRoleAssignment.getId());
				mapping.setCanEdit(false);
				mapping.setStartDate(userUserRoleAssignment.getStartDate());
				mapping.setStopDate(userUserRoleAssignment.getStopDate());
				if (userUserRoleAssignment.getContainsTitles() != ContainsTitles.NO) {
					mapping.setAssignmentType(userUserRoleAssignment.getTitles().size());// assigned through titles
				} else if (userUserRoleAssignment.isContainsExceptedUsers()) {
					mapping.setAssignmentType(0);// Assigned to all with exceptions
				} else {
					mapping.setAssignmentType(userUserRoleAssignment.isInherit() ? -2 : -1);// Assigned to all or with inheritance
				}

				result.add(mapping);
			}
		}

		return result;
	}


	public List<OrgUnitWithRole> getOrgUnitsWithRoleGroup(RoleGroup roleGroup, boolean findIndirectlyAssignedRoles) {
		List<OrgUnitWithRole> result = new ArrayList<>();

		for (OrgUnit orgUnit : orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, false, false).stream().filter(this::isActiveAndIncluded).collect(Collectors.toList())) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}

		if (findIndirectlyAssignedRoles) {
			for (OrgUnit orgUnit : orgUnitDao.findByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, true, false).stream().filter(this::isActiveAndIncluded).collect(Collectors.toList())) {
				OrgUnitWithRole mapping = new OrgUnitWithRole();
				mapping.setOrgUnit(orgUnit);
				mapping.setAssignedThrough(AssignedThrough.DIRECT);

				result.add(mapping);

				if (findIndirectlyAssignedRoles) {
					for (OrgUnit child : orgUnit.getChildren()) {
						getOrgUnitsRecursive(child, result);
					}
				}
			}
		}

		return result;
	}

	private void getOrgUnitsRecursive(OrgUnit orgUnit, List<OrgUnitWithRole> result) {
		for (OrgUnit child : orgUnit.getChildren()) {
			getOrgUnitsRecursive(child, result);
		}

		OrgUnitWithRole mapping = new OrgUnitWithRole();
		mapping.setOrgUnit(orgUnit);
		mapping.setAssignedThrough(AssignedThrough.ORGUNIT);

		result.add(mapping);
	}

	public List<OrgUnitLevel> getAllowedLevels(OrgUnit ou) {
		OrgUnitLevel parentLevel = OrgUnitLevel.NONE;
		OrgUnit o = ou;
		while (o.getParent() != null) {
			if (o.getParent().getLevel() != null && !o.getParent().getLevel().equals(OrgUnitLevel.NONE)) {
				parentLevel = o.getParent().getLevel();
				break;
			}

			o = o.getParent();
		}

		List<OrgUnitLevel> allowedLevels = new ArrayList<>();
		switch (parentLevel) {
			case NONE:
				allowedLevels.add(OrgUnitLevel.LEVEL_1);
				allowedLevels.add(OrgUnitLevel.LEVEL_2);
				allowedLevels.add(OrgUnitLevel.LEVEL_3);
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
				allowedLevels.add(OrgUnitLevel.LEVEL_5);
				allowedLevels.add(OrgUnitLevel.LEVEL_6);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_1:
				allowedLevels.add(OrgUnitLevel.LEVEL_2);
				allowedLevels.add(OrgUnitLevel.LEVEL_3);
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
				allowedLevels.add(OrgUnitLevel.LEVEL_5);
				allowedLevels.add(OrgUnitLevel.LEVEL_6);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_2:
				allowedLevels.add(OrgUnitLevel.LEVEL_3);
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
				allowedLevels.add(OrgUnitLevel.LEVEL_5);
				allowedLevels.add(OrgUnitLevel.LEVEL_6);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_3:
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
				allowedLevels.add(OrgUnitLevel.LEVEL_5);
				allowedLevels.add(OrgUnitLevel.LEVEL_6);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_4:
				allowedLevels.add(OrgUnitLevel.LEVEL_5);
				allowedLevels.add(OrgUnitLevel.LEVEL_6);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_5:
				allowedLevels.add(OrgUnitLevel.LEVEL_6);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			default:
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
		}

		return allowedLevels;
	}

	public List<OrgUnit> getByNextAttestationToday() {
		List<OrgUnit> result = new ArrayList<>();
		List<OrgUnit> orgUnits = orgUnitDao.findByActiveTrueAndNextAttestationNotNull()
				.stream().filter(this::isActiveAndIncluded).collect(Collectors.toList());
		LocalDate today = LocalDate.now();

		for (OrgUnit orgUnit : orgUnits) {
			LocalDate nextAttestation = orgUnit.getNextAttestation().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

			if (nextAttestation.isEqual(today)) {
				result.add(orgUnit);
			}
		}

		return result;
	}

	public List<OrgUnit> getByUuidIn(List<String> uuids) {
		return orgUnitDao.findByUuidIn(uuids);
	}

	@Transactional
	public void cleanupOrgUnitManagers() {
		for (OrgUnit orgUnit : getAll()) {
			boolean flagDeletedManager = false;
			List<AuthorizationManager> currentManagersToRemove = new ArrayList<>();

			for (AuthorizationManager authorizationManager : orgUnit.getAuthorizationManagers()) {

				if (authorizationManager.getUser().isDeleted()) {
					log.info("Removing manager " + authorizationManager.getEntityName() + " from " + orgUnit.getName());
					currentManagersToRemove.add(authorizationManager);

					flagDeletedManager = true;
				}
			}

			if (flagDeletedManager) {
				orgUnit.getAuthorizationManagers().removeAll(currentManagersToRemove);
				save(orgUnit);
			}
		}
	}

	@Transactional
	public void removeRoleAssignmentsWithoutOU() {
		List<User> users = userService.getAll();
		try {
			SecurityUtil.loginSystemAccount();
			for (User user : users) {
				List<OrgUnit> orgUnits = getOrgUnitsForUser(user);
				if (orgUnits.isEmpty()) {
					userService.removeAllDirectlyAssignedRoles(user);
				}
			}
		} finally {
			SecurityUtil.logoutSystemAccount();
		}
	}

	@Transactional
	public void assignResponsibleOuOnAssignments() {
		List<User> users = userService.getAll();
		for (User user : users) {
			List<OrgUnit> orgUnits = getOrgUnitsForUser(user);
			if (orgUnits.isEmpty()) {
				continue;
			}
			for (UserUserRoleAssignment userRoleAssignment : user.getUserRoleAssignments()) {
				if (userRoleAssignment.getOrgUnit() == null) {
					log.info("UserRole does not have responsible OU, assigning users first OU {}", userRoleAssignment.getId());
					userRoleAssignment.setOrgUnit(orgUnits.getFirst());
				}
			}
			for (UserRoleGroupAssignment roleGroupAssignment : user.getRoleGroupAssignments()) {
				if (roleGroupAssignment.getOrgUnit() == null) {
					log.info("RoleGroup does not have responsible OU, assigning users first OU {}", roleGroupAssignment.getId());
					roleGroupAssignment.setOrgUnit(orgUnits.getFirst());
				}
			}
		}
	}

	/**
	 * This methods will look through all direct assignments and check if the user is still associated with the responsible ou
	 * if not the responsible ou will be changed to one of the users current ou's.
	 */
	@Transactional
	public void updateOrgUnitOnRoleAssignments() {
		List<User> users = userService.getAll();
		for (User user : users) {
			List<OrgUnit> orgUnits = getOrgUnitsForUser(user);
			if (orgUnits.isEmpty()) {
				continue;
			}

			boolean changes = false;
			for (UserUserRoleAssignment userRoleAssignment : user.getUserRoleAssignments()) {
				if (userRoleAssignment.getOrgUnit() != null) {
					// check if the orgUnit on the assignment is one of the possible OrgUnits. If not set the role assignment OrgUnit to first of list
					if (orgUnits.stream().noneMatch(o -> o.getUuid().equals(userRoleAssignment.getOrgUnit().getUuid()))) {
						log.debug("Changing OU for roleGroupAssignment: {}", userRoleAssignment.getId());
						userRoleAssignment.setOrgUnit(orgUnits.get(0));
						changes = true;
					}
				}
			}
			for (UserRoleGroupAssignment roleGroupAssignment : user.getRoleGroupAssignments()) {
				if (roleGroupAssignment.getOrgUnit() != null) {
					// check if the orgUnit on the assignment is one of the possible OrgUnits. If not set the role assignment OrgUnit to first of list
					if (orgUnits.stream().noneMatch(o -> o.getUuid().equals(roleGroupAssignment.getOrgUnit().getUuid()))) {
						log.debug("Changing OU for roleGroupAssignment: {}", roleGroupAssignment.getId());
						roleGroupAssignment.setOrgUnit(orgUnits.get(0));
						changes = true;
					}
				}
			}

			if (changes) {
				userService.save(user);
			}
		}

		SecurityUtil.logoutSystemAccount();
	}

	public List<OrgUnit> getOrgUnitsForUser(User user) {
		List<OrgUnit> orgUnits = new ArrayList<>();
		List<String> addedUuids = new ArrayList<>();

		if (user.getPositions() != null) {
			for (Position position : user.getPositions()) {
				if (!addedUuids.contains(position.getOrgUnit().getUuid())) {
					orgUnits.add(position.getOrgUnit());
					addedUuids.add(position.getOrgUnit().getUuid());
				}
			}
		}

		return orgUnits;
	}

	private boolean isExcluded(OrgUnit orgUnit) {
		Set<String> excludedOUUuids = settingsService.getExcludedOUs();
		if (excludedOUUuids.contains(orgUnit.getUuid())) {
			return true;
		} else {
			OrgUnit current = orgUnit;
			while (current.getParent() != null) {
				current = current.getParent();
				if (excludedOUUuids.contains(current.getUuid())) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isActiveAndIncluded(OrgUnit orgUnit) {
		return orgUnit.isActive() && !isExcluded(orgUnit);
	}

}
