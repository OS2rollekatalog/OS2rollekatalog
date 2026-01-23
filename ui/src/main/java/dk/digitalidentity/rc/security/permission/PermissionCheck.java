package dk.digitalidentity.rc.security.permission;

import dk.digitalidentity.rc.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * A convenience class for thymeleaf checks on Permissions
 */
@RequiredArgsConstructor
@Component("permissionCheck")
public class PermissionCheck {
	private final UserPermissionContext userPermissionContext;

	public boolean isAllowed(Section entity, Permission permission) {
		return userPermissionContext.hasPermission(entity, permission);
	}

	public boolean isAnyAllowedForEntity(Section entity, Permission...  permissions) {
		if (entity == null || permissions.length == 0) {
			throw new IllegalArgumentException("Missing arguments in isAnyAllowedForEntity");
		}
		return userPermissionContext.hasAnyPermissionOf(entity, Set.of(permissions));
	}

	public boolean isAnyAllowedForPermission(Permission permission, Section... entities) {
		if (permission == null || entities.length == 0) {
			throw new IllegalArgumentException("Missing arguments in isAnyAllowedForPermission");
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