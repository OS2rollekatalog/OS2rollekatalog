package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.exceptions.OrgUnitNotFoundException;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole;

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
	private OrgUnitService self;
	
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
		return orgUnitDao.getByActiveTrueAndParentIsNull();
	}

	public long countAllWithRoleGroup(RoleGroup role) {
		return orgUnitDao.countByActiveTrueAndRoleGroupAssignmentsRoleGroup(role);
	}

	public long countAllWithRole(UserRole userRole) {
		return orgUnitDao.countByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
	}

	public OrgUnit getByUuid(String uuid) {
		return orgUnitDao.getByUuidAndActiveTrue(uuid);
	}

	// used when we need direct match with manager, ignoring substitutes (e.g. during login)
	public List<OrgUnit> getByManagerMatchingUser(User user) {
		return orgUnitDao.getByManager(user);
	}

	// used when we need a list of orgUnits that the user is a manager for (or a substitute-manager for)
	public List<OrgUnit> getByManager() {
		List<OrgUnit> result = new ArrayList<>();

		String userId = SecurityUtil.getUserId();
		if (userId != null) {
			User user = userDao.getByUserIdAndActiveTrue(userId);

			if (user != null) {
				if (SecurityUtil.hasRole(Constants.ROLE_MANAGER)) {
					result.addAll(getByManagerMatchingUser(user));
				}
				
				if (SecurityUtil.hasRole(Constants.ROLE_SUBSTITUTE)) {
					List<User> managers = securityUtil.getManagersBySubstitute();
					
					for (User manager : managers) {
						result.addAll(getByManagerMatchingUser(manager));
					}
				}
			}
		}
		
		return result;
	}

	public List<OrgUnit> getAll() {
		return orgUnitDao.findByActiveTrue();
	}

	@SuppressWarnings("deprecation")
	public List<OrgUnit> getAllIncludingInactive() {
		return orgUnitDao.findAll();
	}

	@Deprecated
	public List<OrgUnit> getAllWithRoleGroupIncludingInactive(RoleGroup role) {
		return orgUnitDao.getByRoleGroupAssignmentsRoleGroup(role);
	}

	@Deprecated
	public List<OrgUnit> getAllWithRoleIncludingInactive(UserRole userRole) {
		return orgUnitDao.getByUserRoleAssignmentsUserRole(userRole);
	}

	@Cacheable(value = "orgUnits")
	public List<OrgUnit> getAllCached() {
		return orgUnitDao.getByActiveTrue();
	}
	
	@CacheEvict(value = "orgUnits", allEntries = true)
	public void expireOrgUnitCache() {
		;
	}

	@Scheduled(fixedDelay = 5 * 60 * 1000)
	public void resetOrgUnitCache() {
		self.expireOrgUnitCache();
		; // clears cache every 5 minutes
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
	public boolean addRoleGroup(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate, LocalDate stopDate) {
		Optional<OrgUnitRoleGroupAssignment> assignment = ou.getRoleGroupAssignments().stream().filter(r -> r.getRoleGroup().getId() == roleGroup.getId()).findFirst();
		
		if (assignment.isEmpty()) {
			OrgUnitRoleGroupAssignment mapping = new OrgUnitRoleGroupAssignment();
			mapping.setInherit(inherit);
			mapping.setRoleGroup(roleGroup);
			mapping.setOrgUnit(ou);
			mapping.setAssignedByName(SecurityUtil.getUserFullname());
			mapping.setAssignedByUserId(SecurityUtil.getUserId());
			mapping.setAssignedTimestamp(new Date());
			mapping.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			mapping.setStopDate(stopDate);
			mapping.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

			ou.getRoleGroupAssignments().add(mapping);

			return true;
		}
		else {
			// special case to handle inherit modification
			OrgUnitRoleGroupAssignment a = assignment.get();
			if (a.isInherit() != inherit) {
				a.setInherit(inherit);
				
				// update timestamps if needed
				a.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
				a.setStopDate(stopDate);
				a.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

				return true;
			}
		}

		return false;
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
	public boolean addUserRole(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate, LocalDate stopDate) {
		Optional<OrgUnitUserRoleAssignment> assignment = ou.getUserRoleAssignments().stream().filter(r -> r.getUserRole().getId() == userRole.getId()).findFirst();

		if (assignment.isEmpty()) {
			OrgUnitUserRoleAssignment mapping = new OrgUnitUserRoleAssignment();
			mapping.setInherit(inherit);
			mapping.setUserRole(userRole);
			mapping.setOrgUnit(ou);
			mapping.setAssignedByName(SecurityUtil.getUserFullname());
			mapping.setAssignedByUserId(SecurityUtil.getUserId());
			mapping.setAssignedTimestamp(new Date());
			mapping.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
			mapping.setStopDate(stopDate);
			mapping.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);
			ou.getUserRoleAssignments().add(mapping);

			return true;
		}
		else {
			// special case to handle inherit modification
			OrgUnitUserRoleAssignment a = assignment.get();
			if (a.isInherit() != inherit) {
				a.setInherit(inherit);
				
				// update timestamps if needed
				a.setStartDate((startDate == null || LocalDate.now().equals(startDate)) ? null : startDate);
				a.setStopDate(stopDate);
				a.setInactive(startDate != null ? startDate.isAfter(LocalDate.now()) : false);

				return true;
			}			
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeUserRole(OrgUnit ou, UserRole userRole) {
		for (Iterator<OrgUnitUserRoleAssignment> iterator = ou.getUserRoleAssignments().iterator(); iterator.hasNext();) {
			OrgUnitUserRoleAssignment mapping = iterator.next();

			if (mapping.getUserRole().getId() == userRole.getId()) {
				iterator.remove();
				return true;
			}
		}

		return false;
	}

	public List<UserRole> getUserRoles(String uuid, boolean inherit) throws OrgUnitNotFoundException {
		OrgUnit orgUnit = orgUnitDao.getByUuidAndActiveTrue(uuid);
		if (orgUnit == null) {
			throw new OrgUnitNotFoundException("OrgUnit with id '" + uuid + "' was not found in the database");
		}

		return getUserRoles(orgUnit, inherit);
	}
	
	private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Map<Object, Boolean> seen = new ConcurrentHashMap<>();
		return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}

	public List<Title> getTitles(OrgUnit orgUnit) {
		return positionService.findByOrgUnit(orgUnit)
				.stream()
				.filter(p -> p.getTitle() != null && p.getUser().isActive())
				.map(p -> p.getTitle())
				.filter(distinctByKey(t -> t.getUuid()))
				.collect(Collectors.toList());
	}

	public List<UserRole> getUserRoles(OrgUnit orgUnit, boolean inherit) {
		if (inherit) {
			Set<UserRole> resultSet = new HashSet<>();

			getUserRolesRecursive(resultSet, orgUnit, false);

			return new ArrayList<>(resultSet);
		}

		List<UserRole> userRoles = orgUnit.getUserRoleAssignments().stream().map(r -> r.getUserRole()).collect(Collectors.toList());

		if (configuration.getTitles().isEnabled()) {
			List<Title> titles = getTitles(orgUnit);

			for (Title title : titles) {
				for (TitleUserRoleAssignment tura : title.getUserRoleAssignments()) {
					if (tura.getOuUuids().contains(orgUnit.getUuid())) {
						userRoles.add(tura.getUserRole());
					}
				}
			}
		}
		
		return userRoles;
	}

	// TODO: deprecate (do we have interceptors on this?)
	public List<OrgUnitUserRoleAssignment> getRoleMappings(OrgUnit orgUnit) {
		return orgUnit.getUserRoleAssignments();
	}

	// TODO: deprecate (do we have interceptors on this?)
	public List<OrgUnitRoleGroupAssignment> getRoleGroupMappings(OrgUnit orgUnit) {
		return orgUnit.getRoleGroupAssignments();
	}

	private void getUserRolesRecursive(Set<UserRole> resultSet, OrgUnit orgUnit, boolean inheritOnly) {
		if (inheritOnly) {
			List<UserRole> userRoles = orgUnit.getUserRoleAssignments().stream().filter(r -> r.isInherit()).map(r -> r.getUserRole()).collect(Collectors.toList());

			resultSet.addAll(userRoles);
		}
		else {
			List<UserRole> userRoles = orgUnit.getUserRoleAssignments().stream().map(r -> r.getUserRole()).collect(Collectors.toList());

			if (configuration.getTitles().isEnabled()) {
				List<Title> titles = getTitles(orgUnit);

				for (Title title : titles) {
					for (TitleUserRoleAssignment tura : title.getUserRoleAssignments()) {
						if (tura.getOuUuids().contains(orgUnit.getUuid())) {
							userRoles.add(tura.getUserRole());
						}
					}
				}
			}

			resultSet.addAll(userRoles);
		}

		if (orgUnit.getParent() != null) {
			getUserRolesRecursive(resultSet, orgUnit.getParent(), true);
		}
	}

	public List<RoleGroup> getRoleGroups(String uuid, boolean inherit) throws OrgUnitNotFoundException {
		OrgUnit orgUnit = orgUnitDao.getByUuidAndActiveTrue(uuid);
		if (orgUnit == null) {
			throw new OrgUnitNotFoundException("OrgUnit with id '" + uuid + "' was not found in the database");
		}

		return getRoleGroups(orgUnit, inherit);
	}

	public List<RoleGroup> getRoleGroups(OrgUnit orgUnit, boolean inherit) {
		if (inherit) {
			Set<RoleGroup> resultSet = new HashSet<>();

			getUserRoleGroupsRecursive(resultSet, orgUnit, false);

			return new ArrayList<>(resultSet);
		}

		List<RoleGroup> roleGroups = orgUnit.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());

		if (configuration.getTitles().isEnabled()) {
			List<Title> titles = getTitles(orgUnit);

			for (Title title : titles) {
				for (TitleRoleGroupAssignment trga : title.getRoleGroupAssignments()) {
					if (trga.getOuUuids().contains(orgUnit.getUuid())) {
						roleGroups.add(trga.getRoleGroup());
					}
				}
			}
		}

		return roleGroups;
	}

	private void getUserRoleGroupsRecursive(Set<RoleGroup> resultSet, OrgUnit orgUnit, boolean inheritOnly) {
		if (inheritOnly) {
			List<RoleGroup> roleGroups = orgUnit.getRoleGroupAssignments().stream().filter(r -> r.isInherit()).map(r -> r.getRoleGroup()).collect(Collectors.toList());

			resultSet.addAll(roleGroups);
		}
		else {
			List<RoleGroup> roleGroups = orgUnit.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());

			if (configuration.getTitles().isEnabled()) {
				List<Title> titles = getTitles(orgUnit);

				for (Title title : titles) {
					for (TitleRoleGroupAssignment trga : title.getRoleGroupAssignments()) {
						if (trga.getOuUuids().contains(orgUnit.getUuid())) {
							roleGroups.add(trga.getRoleGroup());
						}
					}
				}
			}

			resultSet.addAll(roleGroups);
		}

		if (orgUnit.getParent() != null) {
			getUserRoleGroupsRecursive(resultSet, orgUnit.getParent(), true);
		}
	}

	@Deprecated
	public List<OrgUnit> getByRoleGroup(RoleGroup roleGroup) {
		return orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
	}
	
	public List<OrgUnit> getByRoleGroup(RoleGroup roleGroup, boolean inactive) {
		return orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInactive(roleGroup, inactive);
	}

	@Deprecated
	public List<OrgUnit> getByUserRole(UserRole userRole) {
		return orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
	}
	
	public List<OrgUnit> getByUserRole(UserRole userRole, boolean inactive) {
		return orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInactive(userRole, inactive);
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

	// TODO: does not handle titles yet
	public List<OrgUnitWithRole> getOrgUnitsWithUserRole(UserRole userRole, boolean findIndirectlyAssignedRoles) {
		List<OrgUnitWithRole> result = new ArrayList<>();

		for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(userRole, false, false)) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}
		
		for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritAndUserRoleAssignmentsInactive(userRole, true, false)) {
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
			for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, false, false)) {
				OrgUnitWithRole mapping = new OrgUnitWithRole();
				mapping.setOrgUnit(orgUnit);
				mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);

				result.add(mapping);
			}

			if (findIndirectlyAssignedRoles) {
				// Get all orgUnits that inherit that RoleGroup
				for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, true, false)) {
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

	public List<OrgUnitWithRole> getOrgUnitsWithRoleGroup(RoleGroup roleGroup, boolean findIndirectlyAssignedRoles) {
		List<OrgUnitWithRole> result = new ArrayList<>();

		for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, false, false)) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}

		if (findIndirectlyAssignedRoles) {
			for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritAndRoleGroupAssignmentsInactive(roleGroup, true, false)) {
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
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_1:
				allowedLevels.add(OrgUnitLevel.LEVEL_2);
				allowedLevels.add(OrgUnitLevel.LEVEL_3);
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
				allowedLevels.add(OrgUnitLevel.NONE);
				break;
			case LEVEL_2:
				allowedLevels.add(OrgUnitLevel.NONE);
				allowedLevels.add(OrgUnitLevel.LEVEL_3);
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
				break;
			case LEVEL_3:
				allowedLevels.add(OrgUnitLevel.LEVEL_4);
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
		List<OrgUnit> orgUnits = orgUnitDao.getByActiveTrueAndNextAttestationNotNull();
		LocalDate today = LocalDate.now();
		
		for (OrgUnit orgUnit : orgUnits) {
			LocalDate nextAttestation = orgUnit.getNextAttestation().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			
			if (nextAttestation.isEqual(today)) {
				result.add(orgUnit);
			}
		}
		
		return result;
	}
}
