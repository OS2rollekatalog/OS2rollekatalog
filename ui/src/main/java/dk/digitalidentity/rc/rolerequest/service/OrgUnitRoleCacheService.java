package dk.digitalidentity.rc.rolerequest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.rolerequest.dao.OrgUnitRoleGroupCacheDao;
import dk.digitalidentity.rc.rolerequest.dao.OrgUnitUserRoleCacheDao;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitUserRoleCache;
import dk.digitalidentity.rc.service.OrgUnitService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

    @PersistenceContext
    private EntityManager entityManager;

	public List<OrgUnitUserRoleCache> getUserRoles(final OrgUnit orgUnit) {
		return orgUnitUserRoleCacheDao.getByOrgUnit(orgUnit);
	}

	public List<OrgUnitRoleGroupCache> getRoleGroups(final OrgUnit orgUnit) {
		return orgUnitRoleGroupCacheDao.getByOrgUnit(orgUnit);
	}
	
	public record UserRoleCacheKey(String orgUnitUuid, long userRoleId) {}
	public record RoleGroupCacheKey(String orgUnitUuid, long roleGroupId) {}

	public record RoleCacheDelta(
	        List<UserRoleCacheKey> userRolesToAdd,
	        List<Long> userRoleCacheIdsToDelete,
	        List<RoleGroupCacheKey> roleGroupsToAdd,
	        List<Long> roleGroupCacheIdsToDelete
	) {
	    public boolean isEmpty() {
	        return userRolesToAdd.isEmpty() && userRoleCacheIdsToDelete.isEmpty() && roleGroupsToAdd.isEmpty() && roleGroupCacheIdsToDelete.isEmpty();
	    }
	}

    @Transactional(readOnly = true)
    public RoleCacheDelta computeDelta() {
        List<OrgUnit> orgUnitList = orgUnitService.getAll();

        Map<Object, List<OrgUnitUserRoleCache>> existingUserRoleCacheMap = orgUnitUserRoleCacheDao.findAll().stream()
        	.collect(Collectors.groupingBy(c -> c.getOrgUnit().getUuid()));
        Map<Object, List<OrgUnitRoleGroupCache>> existingRoleGroupCacheMap = orgUnitRoleGroupCacheDao.findAll().stream()
            .collect(Collectors.groupingBy(c -> c.getOrgUnit().getUuid()));

        List<UserRoleCacheKey> userRolesToAdd = new ArrayList<>();
        List<Long> userRoleCacheIdsToDelete = new ArrayList<>();
        List<RoleGroupCacheKey> roleGroupsToAdd = new ArrayList<>();
        List<Long> roleGroupCacheIdsToDelete = new ArrayList<>();

        for (OrgUnit orgUnit : orgUnitList) {

            // 1 - update userRoleCache

            List<OrgUnitUserRoleCache> existingUserRoles = existingUserRoleCacheMap.getOrDefault(orgUnit.getUuid(), List.of());
            List<UserUserRoleAssignment> userUserRoleAssignments = userUserRoleAssignmentDao.findByOrgUnitAndInactiveFalse(orgUnit);

            Set<Long> desiredUserRoleIds = userUserRoleAssignments.stream()
                .map(u -> u.getUserRole().getId())
                .collect(Collectors.toSet());

            Set<Long> existingUserRoleIds = existingUserRoles.stream()
                .map(c -> c.getUserRole().getId())
                .collect(Collectors.toSet());

            for (Long userRoleId : desiredUserRoleIds) {
                if (!existingUserRoleIds.contains(userRoleId)) {
                    userRolesToAdd.add(new UserRoleCacheKey(orgUnit.getUuid(), userRoleId));
                }
            }

            for (OrgUnitUserRoleCache oldCache : existingUserRoles) {
                if (!desiredUserRoleIds.contains(oldCache.getUserRole().getId())) {
                    userRoleCacheIdsToDelete.add(oldCache.getId());
                }
            }

            // 2 - update roleGroupCache

            List<OrgUnitRoleGroupCache> existingRoleGroups = existingRoleGroupCacheMap.getOrDefault(orgUnit.getUuid(), List.of());
            List<UserRoleGroupAssignment> userRoleGroupAssignments = userRoleGroupAssignmentDao.findByOrgUnitAndInactiveFalse(orgUnit);

            Set<Long> desiredRoleGroupIds = userRoleGroupAssignments.stream()
                .map(u -> u.getRoleGroup().getId())
                .collect(Collectors.toSet());

            Set<Long> existingRoleGroupIds = existingRoleGroups.stream()
                .map(c -> c.getRoleGroup().getId())
                .collect(Collectors.toSet());

            for (Long roleGroupId : desiredRoleGroupIds) {
                if (!existingRoleGroupIds.contains(roleGroupId)) {
                    roleGroupsToAdd.add(new RoleGroupCacheKey(orgUnit.getUuid(), roleGroupId));
                }
            }

            for (OrgUnitRoleGroupCache oldCache : existingRoleGroups) {
                if (!desiredRoleGroupIds.contains(oldCache.getRoleGroup().getId())) {
                    roleGroupCacheIdsToDelete.add(oldCache.getId());
                }
            }
        }

        return new RoleCacheDelta(userRolesToAdd, userRoleCacheIdsToDelete, roleGroupsToAdd, roleGroupCacheIdsToDelete);
    }

    @Transactional
    public void apply(RoleCacheDelta delta) {
        if (!delta.userRoleCacheIdsToDelete().isEmpty()) {
            log.info("Removing " + delta.userRoleCacheIdsToDelete().size() + " OrgUnitUserRoleCache entries");

            orgUnitUserRoleCacheDao.deleteByIdIn(delta.userRoleCacheIdsToDelete());
        }

        if (!delta.roleGroupCacheIdsToDelete().isEmpty()) {
            log.info("Removing " + delta.roleGroupCacheIdsToDelete().size() + " OrgUnitRoleGroupCache entries");

            orgUnitRoleGroupCacheDao.deleteByIdIn(delta.roleGroupCacheIdsToDelete());
        }

        if (!delta.userRolesToAdd().isEmpty()) {
            log.info("Creating " + delta.userRolesToAdd().size() + " new OrgUnitUserRoleCache entries");

            orgUnitUserRoleCacheDao.saveAll(delta.userRolesToAdd().stream()
                .map(k -> new OrgUnitUserRoleCache(0,
                		entityManager.getReference(OrgUnit.class, k.orgUnitUuid()),
                		entityManager.getReference(UserRole.class, k.userRoleId())))
                .toList());
        }

        if (!delta.roleGroupsToAdd().isEmpty()) {
            log.info("Creating " + delta.roleGroupsToAdd().size() + " new OrgUnitRoleGroupCache entries");

            orgUnitRoleGroupCacheDao.saveAll(delta.roleGroupsToAdd().stream()
                .map(k -> new OrgUnitRoleGroupCache(0,
                		entityManager.getReference(OrgUnit.class, k.orgUnitUuid()),
                		entityManager.getReference(RoleGroup.class, k.roleGroupId())))
                .toList());
        }
    }
}
