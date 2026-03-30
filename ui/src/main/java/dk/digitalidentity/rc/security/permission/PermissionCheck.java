package dk.digitalidentity.rc.security.permission;

import dk.digitalidentity.rc.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * A convenience class for thymeleaf checks on Permissions
 */
@Slf4j
@RequiredArgsConstructor
@Component("permissionCheck")
public class PermissionCheck {
	private final UserPermissionContext userPermissionContext;

	public boolean isAllowed(Section entity, Permission permission) {
		return userPermissionContext.hasPermission(entity, permission);
	}

	public boolean isAnyAllowedForEntity(Section section, Permission...  permissions) {
		if (section == null || permissions.length == 0) {
			log.warn("Permission check was called with a missing argument. Section: {}, Permissions: {}", section, permissions);
			return false;
		}
		return userPermissionContext.hasAnyPermissionOf(section, Set.of(permissions));
	}

	public boolean isAnyAllowedForPermission(Permission permission, Section... entities) {
		if (permission == null || entities.length == 0) {
			log.warn("Permission check was called with a missing argument. Permission: {}, Sections: {}", permission, entities);
			return false;
		}
		return userPermissionContext.hasPermissionForAnyOf(Set.of(entities), permission);
	}

	public boolean isRequester() {
		return SecurityUtil.isRequesterAndOnlyRequester();
	}

	public boolean isReportTemplateAllowed(Permission permission){
		return userPermissionContext.isReportTemplateAllowed(permission);
	}
}
