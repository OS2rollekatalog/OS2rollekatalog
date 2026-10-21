package dk.digitalidentity.rc.rolerequest.model.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One user role or role group in a role request submission, with the period it is requested for.
 */
public record RequestedRole(Long userRoleId, Long roleGroupId, LocalDate startDate, LocalDate stopDate) {

	public RequestedRole {
		if ((userRoleId == null) == (roleGroupId == null)) {
			throw new IllegalArgumentException("Exactly one of userRoleId and roleGroupId must be set");
		}
	}

	public static RequestedRole userRole(Long userRoleId, LocalDate startDate, LocalDate stopDate) {
		return new RequestedRole(Objects.requireNonNull(userRoleId, "userRoleId"), null, startDate, stopDate);
	}

	public static RequestedRole roleGroup(Long roleGroupId, LocalDate startDate, LocalDate stopDate) {
		return new RequestedRole(null, Objects.requireNonNull(roleGroupId, "roleGroupId"), startDate, stopDate);
	}
}
