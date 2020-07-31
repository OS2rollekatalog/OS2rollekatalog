package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
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
public class OrgUnitService {

	@Autowired
	private OrgUnitDao orgUnitDao;
	
	@Autowired
	private UserDao userDao;

	@Autowired
	private RoleGroupDao roleGroupDao;
	
	@Autowired
	private SecurityUtil securityUtil;
	
	// dao methods

	public OrgUnit save(OrgUnit orgUnit) {
		return orgUnitDao.save(orgUnit);
	}

	public Iterable<OrgUnit> save(Iterable<OrgUnit> orgUnits) {
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

	public List<OrgUnit> getByUserRole(UserRole userRole) {
		return orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
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

	public List<OrgUnit> getByRoleGroup(RoleGroup roleGroup) {
		return orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
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

	@Scheduled(fixedRate = 5 * 60 * 1000)
	@CacheEvict(value = "orgUnits", allEntries = true)
	public void resetOrgUnitCache() {
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
	public boolean addRoleGroup(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		List<RoleGroup> roleGroups = ou.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());

		if (!roleGroups.contains(roleGroup)) {
			OrgUnitRoleGroupAssignment mapping = new OrgUnitRoleGroupAssignment();
			mapping.setInherit(inherit);
			mapping.setRoleGroup(roleGroup);
			mapping.setOrgUnit(ou);
			mapping.setAssignedByName(SecurityUtil.getUserFullname());
			mapping.setAssignedByUserId(SecurityUtil.getUserId());
			mapping.setAssignedTimestamp(new Date());

			ou.getRoleGroupAssignments().add(mapping);

			return true;
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
	public boolean addUserRole(OrgUnit ou, UserRole userRole, boolean inherit) {
		List<UserRole> userRoles = ou.getUserRoleAssignments().stream().map(r -> r.getUserRole()).collect(Collectors.toList());

		if (!userRoles.contains(userRole)) {
			OrgUnitUserRoleAssignment mapping = new OrgUnitUserRoleAssignment();
			mapping.setInherit(inherit);
			mapping.setUserRole(userRole);
			mapping.setOrgUnit(ou);
			mapping.setAssignedByName(SecurityUtil.getUserFullname());
			mapping.setAssignedByUserId(SecurityUtil.getUserId());
			mapping.setAssignedTimestamp(new Date());

			ou.getUserRoleAssignments().add(mapping);

			return true;
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

	public List<UserRole> getUserRoles(OrgUnit orgUnit, boolean inherit) {
		if (inherit) {
			Set<UserRole> resultSet = new HashSet<>();

			getUserRolesRecursive(resultSet, orgUnit, false);

			return new ArrayList<>(resultSet);
		}

		return orgUnit.getUserRoleAssignments().stream().map(r -> r.getUserRole()).collect(Collectors.toList());
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

		return orgUnit.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());
	}

	private void getUserRoleGroupsRecursive(Set<RoleGroup> resultSet, OrgUnit orgUnit, boolean inheritOnly) {
		if (inheritOnly) {
			List<RoleGroup> roleGroups = orgUnit.getRoleGroupAssignments().stream().filter(r -> r.isInherit()).map(r -> r.getRoleGroup()).collect(Collectors.toList());

			resultSet.addAll(roleGroups);
		}
		else {
			List<RoleGroup> roleGroups = orgUnit.getRoleGroupAssignments().stream().map(r -> r.getRoleGroup()).collect(Collectors.toList());

			resultSet.addAll(roleGroups);
		}

		if (orgUnit.getParent() != null) {
			getUserRoleGroupsRecursive(resultSet, orgUnit.getParent(), true);
		}
	}

	public List<OrgUnit> getByActiveTrueAndRoleGroupMappingsRoleGroup(RoleGroup roleGroup) {
		return orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroup(roleGroup);
	}

	public List<OrgUnit> getByActiveTrueAndRoleMappingsUserRole(UserRole userRole) {
		return orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRole(userRole);
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

	public List<OrgUnitWithRole> getOrgUnitsWithUserRole(UserRole userRole, boolean findIndirectlyAssignedRoles) {
		List<OrgUnitWithRole> result = new ArrayList<>();

		for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritFalse(userRole)) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}
		
		for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndUserRoleAssignmentsUserRoleAndUserRoleAssignmentsInheritTrue(userRole)) {
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
			for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritFalse(roleGroup)) {
				OrgUnitWithRole mapping = new OrgUnitWithRole();
				mapping.setOrgUnit(orgUnit);
				mapping.setAssignedThrough(AssignedThrough.ROLEGROUP);

				result.add(mapping);
			}

			if (findIndirectlyAssignedRoles) {
				// Get all orgUnits that inherit that RoleGroup
				for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritTrue(roleGroup)) {
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

		for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritFalse(roleGroup)) {
			OrgUnitWithRole mapping = new OrgUnitWithRole();
			mapping.setOrgUnit(orgUnit);
			mapping.setAssignedThrough(AssignedThrough.DIRECT);

			result.add(mapping);
		}

		if (findIndirectlyAssignedRoles) {
			for (OrgUnit orgUnit : orgUnitDao.getByActiveTrueAndRoleGroupAssignmentsRoleGroupAndRoleGroupAssignmentsInheritTrue(roleGroup)) {
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
