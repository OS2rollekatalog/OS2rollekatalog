package dk.digitalidentity.rc.rolerequest.service.permission;

import java.util.Set;

public record PermittedRoleIds(Set<Long> userRoleIds, Set<Long> roleGroupIds) {
}
