package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import dk.digitalidentity.rc.dao.history.model.GenericRoleAssignment;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.Builder;
import lombok.Getter;

/**
 * A single row of the Excel report's OU-role sheets, flattened from {@code historic_ou_assignment}.
 * One assignment that inherits to children yields one row per org unit in the subtree, so the
 * {@code ouUuid} and {@code assignedThrough*} fields here may differ from the stored assignment.
 */
@Builder
@Getter
public class OuRoleAssignmentReportRow implements GenericRoleAssignment {

	private final String ouUuid;
	private final long roleId;
	private final String roleName;
	private final Long itSystemId;
	private final String itSystemName;
	private final String roleRoleGroup;
	private final Long roleRoleGroupId;
	private final String assignedByUserId;
	private final String assignedByName;
	private final LocalDateTime assignedWhen;
	private final LocalDate startDate;
	private final LocalDate stopDate;
	private final AssignedThrough assignedThroughType;
	private final String assignedThroughUuid;
	private final String assignedThroughName;
	private final boolean manager;
	private final boolean substitutes;

	/** True only on the org unit where an inheriting assignment was made, false on descendant rows. */
	private final boolean inherit;

	private final List<String> exceptedUserUuids;
	private final List<String> titleUuids;
	private final List<String> functionUuids;
}
