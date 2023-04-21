package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ItSystemService {

	@Autowired
	private ItSystemDao itSystemDao;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private PositionService positionService;

	@Autowired
	private RoleGroupService roleGroupService;
		
	public List<ItSystem> getAll() {
		List<ItSystem> result = itSystemDao.findAll();
		result = filterDeleted(result);

		return result.stream().sorted((o1, o2) -> o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase())).collect(Collectors.toList());
	}

	public ItSystem getFirstByIdentifier(String identifier) {
		List<ItSystem> result = itSystemDao.findByIdentifier(identifier);
		result = filterDeleted(result);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public ItSystem getFirstByName(String name) {
		List<ItSystem> result = itSystemDao.findByIdentifier(name);
		result = filterDeleted(result);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public ItSystem save(ItSystem itSystem) {
		return itSystemDao.save(itSystem);
	}

	public ItSystem getById(long id) {
		return filterDeleted(itSystemDao.findById(id));
	}

	public ItSystem getByUuid(String uuid) {
		return filterDeleted(itSystemDao.findByUuid(uuid));
	}
	
	public ItSystem getByUuidIncludingDeleted(String uuid) {
		return itSystemDao.findByUuid(uuid);
	}

	public List<ItSystem> getBySystemType(ItSystemType systemType) {
		return filterDeleted(itSystemDao.findBySystemType(systemType));
	}

	public List<ItSystem> getBySystemTypeIn(List<ItSystemType> systemTypes) {
		return filterDeleted(itSystemDao.findBySystemTypeIn(systemTypes));
	}
	
	public List<ItSystem> getBySystemTypeIncludingDeleted(ItSystemType systemType) {
		return itSystemDao.findBySystemType(systemType);
	}

	public List<ItSystem> getBySubscribedToNotNull() {
		return filterDeleted(itSystemDao.findBySubscribedToNotNull());
	}

	public long count() {
		return itSystemDao.countByDeletedFalseAndHiddenFalse();
	}

	public List<ItSystem> findByIdentifier(String identifier) {
		return filterDeleted(itSystemDao.findByIdentifier(identifier));
	}

	// TODO: use count
	@SuppressWarnings("deprecation")
	public int getUnusedUserRolesCount(ItSystem itSystem) {
		int sum = 0;
		
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			if (userService.countAllWithRole(userRole) > 0) {
				continue;
			}
			
			if (orgUnitService.countAllWithRole(userRole) > 0) {
				continue;
			}
			
			if (positionService.getAllWithRole(userRole).size() > 0) {
				continue;
			}

			sum++;
		}
		
		return sum;
	}
	
	public List<ItSystem> getVisible() {
		List<ItSystem> result = itSystemDao.findByHiddenFalse();
		result = filterDeleted(result);
		
		return result.stream().sorted((o1, o2) -> o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase())).collect(Collectors.toList());
	}

	public List<String> getOUFilterUuidsWithChildren(ItSystem itSystem) {
		Set<String> selectedOUs = new HashSet<>();
		for (OrgUnit ou : itSystem.getOrgUnitFilterOrgUnits()) {
			if (!selectedOUs.contains(ou.getUuid())) {
				addChildrenRecursive(ou, selectedOUs);
			}
		}

		return new ArrayList<>(selectedOUs);
	}

	private void addChildrenRecursive(OrgUnit ou, Set<String> selectedOUs) {
		selectedOUs.add(ou.getUuid());

		if (ou.getChildren() == null || ou.getChildren().isEmpty()) {
			return;
		}

		for (OrgUnit child : ou.getChildren()) {
			addChildrenRecursive(child, selectedOUs);
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void permanentlyDelete() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.DAY_OF_MONTH, -3);
		Date sixMonthsAgo = cal.getTime();

		List<ItSystem> deleted = itSystemDao.findByDeletedTrue();
		for (ItSystem itSystem : deleted) {
			if (itSystem.getDeletedTimestamp().before(sixMonthsAgo)) {
				log.info("Attempting to delete it-system " + itSystem.getName() + " with id = " + itSystem.getId());

				// these are build-in, cannot delete
				if (itSystem.getSystemType().equals(ItSystemType.KSPCICS) || itSystem.getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)) {
					continue;
				}

				// remove affected user roles from role groups
				List<RoleGroup> roleGroups = roleGroupService.getAll();
				for (RoleGroup roleGroup : roleGroups) {
					List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream()
							.filter(ura -> (ura.getUserRole().getItSystem().getId() == itSystem.getId()))
							.map(ura -> ura.getUserRole()).collect(Collectors.toList());

					if (userRoles != null && userRoles.size() > 0) {
						for (UserRole userRole : userRoles) {
							roleGroupService.removeUserRole(roleGroup, userRole);
						}

						roleGroupService.save(roleGroup);
					}
				}

				// delete affected user roles
				List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
				for (UserRole userRole : userRoles) {
					userRoleService.delete(userRole);
				}

				// delete affected system roles
				List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
				for (SystemRole systemRole : systemRoles) {
					systemRoleService.delete(systemRole);
				}

				// delete itsystem
				itSystemDao.delete(itSystem);
			}
		}
	}

	//UTILITY METHODS
	
	private List<ItSystem> filterDeleted(List<ItSystem> itSystems) {
		return itSystems == null ? null
				: itSystems.stream().filter(its -> its.isDeleted() == false).collect(Collectors.toList());
	}

	private ItSystem filterDeleted(ItSystem itSystem) {
		if (itSystem == null) {
			return null;
		}
		return itSystem.isDeleted() ? null : itSystem;
	}
}
