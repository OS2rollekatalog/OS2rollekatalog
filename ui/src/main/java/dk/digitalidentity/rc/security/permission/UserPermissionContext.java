package dk.digitalidentity.rc.security.permission;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.permission.PermissionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Stores the permission context for the current user.
 * Contains methods to get and validate the current users access permissions
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserPermissionContext {
	private final PermissionService permissionService;

	private volatile Map<Section, Map<Permission, PermissionConstraint>> permissions;

	@PostConstruct
	public void init() {
		String userUuid = SecurityUtil.getUserUuid();
		if (userUuid == null) {
			this.permissions = Map.of();
			log.warn("User id not found when initializing permission context");
			return;
		}

		Map<Section, Map<Permission, PermissionConstraint>> permissionMap = permissionService.getPermissionMap(userUuid);

		this.permissions = permissionMap != null
			? Map.copyOf(permissionMap)
			:  Map.of();
	}

	public PermissionConstraint getConstraint(Section section, Permission permission) {
		Map<Permission, PermissionConstraint> permissionMap = getConstraintsPerPermission(section);
		if (permissionMap.containsKey(permission)) {
			// If there is a permission for this section, return either the contraons or full access
			PermissionConstraint constraint = permissionMap.get(permission);
			return constraint == null ? new PermissionConstraint(null, null) : constraint;
		}
		// Otherwise, return a no-access constraint;
		return new PermissionConstraint(Set.of(), Set.of());
	}

	public boolean hasPermission(Section section, Permission permission) {
		return permissions.containsKey(section)
				&& permissions.get(section).containsKey(permission);
	}

	public boolean hasAnyPermissionOf(Section section, Collection<Permission> permission) {
		if (permission == null || permission.isEmpty() || section == null) {
			return false;
		}
		Map<Permission, PermissionConstraint> permissionMap = permissions.get(section);
		if (permissionMap != null && !permissionMap.isEmpty()) {
			return permissionMap.keySet().stream().anyMatch(permission::contains);
		}
		return false;
	}

	public boolean hasPermissionForAnyOf (Collection<Section> sections, Permission permission) {
		if (permission == null || sections == null || sections.isEmpty()) {
			return false;
		}

		for (Section section : sections) {
			Map<Permission, PermissionConstraint> permissionMap = permissions.get(section);
			if (permissionMap != null && !permissionMap.isEmpty()) {
				boolean hasPermission = permissionMap.containsKey(permission);
				if (hasPermission) { return true; }
			}
		}
		return false;
	}

	public static Set<Permission> getPermissions(Map<Section, Map<Permission, PermissionConstraint>> permissionMap, Section section) {
		if (permissionMap == null || section == null || permissionMap.isEmpty()) {
			return Set.of();
		}
		return permissionMap.getOrDefault(section, new EnumMap<>(Permission.class)).keySet();

	}

	public ItemPermissionDTO getAllowedActionsForSection(Section section) {
		Set<Permission> permissionSet = getPermissions(permissions, section);
		return new ItemPermissionDTO(
				permissionSet.contains(Permission.CREATE),
				permissionSet.contains(Permission.READ),
				permissionSet.contains(Permission.UPDATE),
				permissionSet.contains(Permission.DELETE)
		);
	}

	public ItemPermissionDTO getSpecificAllowedActionsForOu(Section section, String ouUuid) {
		return new ItemPermissionDTO(
				getConstraint(section, Permission.CREATE).allowsOrgunit(ouUuid),
				getConstraint(section, Permission.READ).allowsOrgunit(ouUuid),
				getConstraint(section, Permission.UPDATE).allowsOrgunit(ouUuid),
				getConstraint(section, Permission.DELETE).allowsOrgunit(ouUuid)
		);
	}

	public ItemPermissionDTO getSpecificAllowedActionsForITsystem(Section section, Long itSystemId) {
		return new ItemPermissionDTO(
				getConstraint(section, Permission.CREATE).allowsITSystem(itSystemId),
				getConstraint(section, Permission.READ).allowsITSystem(itSystemId),
				getConstraint(section, Permission.UPDATE).allowsITSystem(itSystemId),
				getConstraint(section, Permission.DELETE).allowsITSystem(itSystemId)
		);
	}

	public ItemPermissionDTO getSpecificAllowedActionsForOus(Section section, Set<String> ouUuid) {
		Set<String> createConstraint = getConstraint(section, Permission.CREATE).getConstrainedOUUuids();
		Set<String> readConstraint = getConstraint(section, Permission.READ).getConstrainedOUUuids();
		Set<String> updateConstraint = getConstraint(section, Permission.UPDATE).getConstrainedOUUuids();
		Set<String> deleteConstraint = getConstraint(section, Permission.DELETE).getConstrainedOUUuids();

		return new ItemPermissionDTO(
				createConstraint== null || createConstraint.stream().anyMatch(ouUuid::contains),
				readConstraint== null ||readConstraint.stream().anyMatch(ouUuid::contains),
				updateConstraint== null ||updateConstraint.stream().anyMatch(ouUuid::contains),
				deleteConstraint== null ||deleteConstraint.stream().anyMatch(ouUuid::contains)
		);
	}



	public PermissionConstraint getConstraints(Section section, Permission permission) {
		return permissions
				.getOrDefault(section, Collections.emptyMap())
				.getOrDefault(permission, new PermissionConstraint(Set.of(), Set.of()));
	}

	public Map<Permission, PermissionConstraint> getConstraintsPerPermission (Section section) {
		return permissions.getOrDefault(section, new EnumMap<>(Permission.class));
	}

	public boolean isReportTemplateAllowed(Permission permission){
		return hasPermission(Section.REPORT, permission)
				|| SecurityUtil.hasRole(Constants.ROLE_TEMPLATE_ACCESS);
	}
}
