package dk.digitalidentity.rc.service.permission;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermission;
import dk.digitalidentity.rc.dao.permission.UserPermissionDao;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PermissionService {
	private final UserPermissionDao userPermissionDao;
	private final OrgUnitService orgUnitService;

	/**
	 * Loads all permissions for a user
	 */
//	@Cacheable(value = "userPermissions", key = "#userUuid")
	// TODO Cachable, we need some way to evict on multiple instances before enabling this.
	public Map<Section, Map<Permission, PermissionConstraint>> getPermissionMap(String userUuid) {
		List<UserPermission> permissions = userPermissionDao.findByUserUuid(userUuid);

		// Build the nested map structure
		Map<Section, Map<Permission, PermissionConstraint>> result = new EnumMap<>(Section.class);

		for (UserPermission permission : permissions) {
			result.computeIfAbsent(permission.getSection(), k -> new EnumMap<>(Permission.class))
					.put(permission.getPermission(), convertToConstraint(permission));
		}

		return result;
	}

	/**
	 * Convert database section to PermissionConstraint
	 */
	private PermissionConstraint convertToConstraint(UserPermission permission) {
		Set<Long> itSystemIds = parseIdSet(permission.getConstrainedItSystemIds());
		Set<String> ouUuids = parseUuidSet(permission.getConstrainedOuUuids());
		return new PermissionConstraint(itSystemIds, ouUuids);
	}

	private Set<Long> parseIdSet(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return null;  // null = unconstrained
		}
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.map(Long::parseLong)
				.collect(Collectors.toSet());
	}

	private Set<String> parseUuidSet(String csv) {
		if (csv == null || csv.trim().isEmpty()) {
			return null;  // null = unconstrained
		}
		return new HashSet<>(Arrays.asList(csv.split(",")));
	}

	/**
	 * Save/update user permissions
	 */
	@Transactional
//	@CacheEvict(value = "userPermissions", key = "#userUuid")
	public void saveUserPermissions(String userUuid, Map<Section, Map<Permission, PermissionConstraint>> permissions) {

		// Delete existing permissions
		userPermissionDao.deleteByUserId(userUuid);

		// Insert new permissions
		List<UserPermission> permissionEntries = new ArrayList<>();

		for (Map.Entry<Section, Map<Permission, PermissionConstraint>> permissionSectionEntry : permissions.entrySet()) {

			for (Map.Entry<Permission, PermissionConstraint> permEntry
					: permissionSectionEntry.getValue().entrySet()) {

				UserPermission permissionEntry = new UserPermission();
				permissionEntry.setUserUuid(userUuid);
				permissionEntry.setSection(permissionSectionEntry.getKey());
				permissionEntry.setPermission(permEntry.getKey());

				PermissionConstraint constraint = permEntry.getValue();
				permissionEntry.setConstrainedItSystemIds(serializeIdSet(constraint.getConstrainedItSystemIds()));
				permissionEntry.setConstrainedOuUuids(serializeUuidSet(constraint.getConstrainedOUUuids()));

				permissionEntries.add(permissionEntry);
			}
		}

		userPermissionDao.saveAll(permissionEntries);
	}

	private String serializeIdSet(Set<Long> ids) {
		if (ids == null) return null;
		return ids.stream()
				.map(String::valueOf)
				.collect(Collectors.joining(","));
	}

	private String serializeUuidSet(Set<String> uuids) {
		if (uuids == null) return null;
		return String.join(",", uuids);
	}

	public void addLimitedManagerAccess(String userUuid) {
		Set<String> constrainedOUUuids = orgUnitService.getByManager().stream()
				.map(OrgUnit::getUuid)
				.collect(Collectors.toSet());

		Set<Permission> permissions = Set.of(
				Permission.READ,
				Permission.CREATE,
				Permission.UPDATE,
				Permission.DELETE
		);

		for (Permission permission : permissions) {
			updateConstraintFor( Section.MANAGER ,permission, userUuid, new PermissionConstraint(null, constrainedOUUuids));
		}
	}

//	@CacheEvict(value = "userPermissions", key = "#userUuid")
	public void updateConstraintFor(Section section, Permission permission, String userUuid, PermissionConstraint updatedConstraint) {
		// Check if permission already exists
		UserPermission existingPermission = userPermissionDao.findByUserUuidAndSectionAndPermission(userUuid, section, permission)
				.orElse(null);

		if (existingPermission == null) {
			existingPermission = UserPermission.builder()
					.userUuid(userUuid)
					.section(section)
					.permission(permission)
					.constrainedItSystemIds(serializeIdSet(updatedConstraint.getConstrainedItSystemIds()))
					.constrainedOuUuids(serializeUuidSet(updatedConstraint.getConstrainedOUUuids()))
					.build();
		} else {
			// Update existing permission with merged constraints
			PermissionConstraint existingConstraint = convertToConstraint(existingPermission);
			PermissionConstraint mergedConstraint = existingConstraint.merge(updatedConstraint);

			existingPermission.setConstrainedItSystemIds(serializeIdSet(mergedConstraint.getConstrainedItSystemIds()));
			existingPermission.setConstrainedOuUuids(serializeUuidSet(mergedConstraint.getConstrainedOUUuids()));
		}

			userPermissionDao.save(existingPermission);
	}

	public void addRightsReadAccess(String userUuid) {
		for (Section section : List.of(
				Section.USER_ROLE,
				Section.ROLE_GROUP,
				Section.IT_SYSTEM,
				Section.USER,
				Section.ORGUNIT)) {
			updateConstraintFor(section,Permission.READ, userUuid, new PermissionConstraint(null, null));
		}
	}

	public void addFullAccess(Section section, String userUuid) {
		updateConstraintFor(section ,Permission.READ, userUuid, new PermissionConstraint(null, null));
		updateConstraintFor( section ,Permission.CREATE, userUuid, new PermissionConstraint(null, null));
		updateConstraintFor( section ,Permission.UPDATE, userUuid, new PermissionConstraint(null, null));
		updateConstraintFor( section ,Permission.DELETE, userUuid, new PermissionConstraint(null, null));
	}

}
