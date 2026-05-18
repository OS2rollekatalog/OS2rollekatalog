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
			// If there is a permission for this section, return either the constraints or full access
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

	public boolean hasAnyPermissionOf(final Section section, final Collection<Permission> permissions) {
		if (permissions == null || permissions.isEmpty() || section == null) {
			log.warn("Permission or Section are empty. Section: {}, Permissions: {}", section, permissions);
			return false;
		}
		return permissions.stream().anyMatch(p -> hasPermission(section, p));
	}

	public boolean hasPermissionForAnyOf (final Collection<Section> sections, final Permission permission) {
		if (permission == null || sections == null || sections.isEmpty()) {
			log.warn("Permission or Section are empty. Sections: {}, Permissions: {}", sections, permission);
			return false;
		}

		return sections.stream().anyMatch(section -> hasPermission(section, permission));
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

	public Map<Permission, PermissionConstraint> getConstraintsPerPermission (Section section) {
		return permissions.getOrDefault(section, new EnumMap<>(Permission.class));
	}

	public boolean isReportTemplateAllowed(Permission permission){
		return hasPermission(Section.REPORT, permission)
				|| SecurityUtil.hasRole(Constants.ROLE_TEMPLATE_ACCESS);
	}

	/**
	 * Whether the current user is allowed to create/edit/delete substitute relationships.
	 * Substitutes acting on behalf of a leader must NOT propagate substitute-creation —
	 * that authority belongs to the actual leader, administrators, and users with the
	 * "Ledere - Administrer" system role (which grants unconstrained MANAGER UPDATE).
	 */
	public boolean canManageSubstitutes() {
		if (SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR) || SecurityUtil.hasRole(Constants.ROLE_MANAGER)) {
			return true;
		}
		// Substitute access is always OU-constrained; unconstrained UPDATE means access from another source
		PermissionConstraint update = getConstraint(Section.MANAGER, Permission.UPDATE);
		return update.getConstrainedOUUuids() == null;
	}
}
