package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.rolerequest.dao.OrgUnitRoleGroupCacheDao;
import dk.digitalidentity.rc.rolerequest.dao.OrgUnitUserRoleCacheDao;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitUserRoleCache;
import dk.digitalidentity.rc.service.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrgUnitRoleCacheService {

	@Autowired
	private OrgUnitUserRoleCacheDao orgUnitUserRoleCacheDao;

	@Autowired
	private OrgUnitRoleGroupCacheDao orgUnitRoleGroupCacheDao;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserUserRoleAssignmentDao userUserRoleAssignmentDao;

	@Autowired
	private UserRoleGroupAssignmentDao userRoleGroupAssignmentDao;

	private void deleteCurrentOrgUnitUserRoleCache(OrgUnit orgUnit) {
		orgUnitUserRoleCacheDao.deleteByOrgUnit(orgUnit);
	}

	private void deleteCurrentOrgUnitRoleGroupCache(OrgUnit orgUnit) {
		orgUnitRoleGroupCacheDao.deleteByOrgUnit(orgUnit);
	}

	private void saveAllOrgUnitUserRoleCache(List<OrgUnitUserRoleCache> userRoleCaches) {
		orgUnitUserRoleCacheDao.saveAll(userRoleCaches);
	}

	private void saveAllOrgUnitRoleGroupCache(List<OrgUnitRoleGroupCache> roleGroupCaches) {
		orgUnitRoleGroupCacheDao.saveAll(roleGroupCaches);
	}

	public List<OrgUnitUserRoleCache> getUserRoles(final OrgUnit orgUnit) {
		return orgUnitUserRoleCacheDao.getByOrgUnit(orgUnit);
	}

	public List<OrgUnitRoleGroupCache> getRoleGroups(OrgUnit orgUnit) {
		return orgUnitRoleGroupCacheDao.getByOrgUnit(orgUnit);
	}

	@Transactional
	public void saveCurrentOrgUnitRoles() {
		List<OrgUnit> orgUnitList = orgUnitService.getAll();
		for (OrgUnit orgUnit : orgUnitList) {

			// delete old in db
			deleteCurrentOrgUnitUserRoleCache(orgUnit);
			deleteCurrentOrgUnitRoleGroupCache(orgUnit);

			// add new for all direct role assignments and avoid duplicate roles
			List<UserUserRoleAssignment> userUserRoleAssignments = userUserRoleAssignmentDao.findByOrgUnitAndInactiveFalse(orgUnit);
			List<UserRoleGroupAssignment> userRoleGroupAssignments = userRoleGroupAssignmentDao.findByOrgUnitAndInactiveFalse(orgUnit);

			List<OrgUnitUserRoleCache> userRoleCaches = new ArrayList<>(
					userUserRoleAssignments.stream()
							.collect(Collectors.toMap(
									u -> u.getUserRole().getId(),
									u -> new OrgUnitUserRoleCache(0, orgUnit, u.getUserRole()),
									(existing, replacement) -> existing
							))
							.values()
			);

			List<OrgUnitRoleGroupCache> roleGroupCaches = new ArrayList<>(
					userRoleGroupAssignments.stream()
							.collect(Collectors.toMap(
									u -> u.getRoleGroup().getId(),
									u -> new OrgUnitRoleGroupCache(0, orgUnit, u.getRoleGroup()),
									(existing, replacement) -> existing
							))
							.values()
			);

			saveAllOrgUnitUserRoleCache(userRoleCaches);
			saveAllOrgUnitRoleGroupCache(roleGroupCaches);
		}
	}
}
