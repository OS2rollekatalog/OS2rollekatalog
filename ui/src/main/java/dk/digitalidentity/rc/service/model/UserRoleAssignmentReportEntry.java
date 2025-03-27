package dk.digitalidentity.rc.service.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
public class UserRoleAssignmentReportEntry {
	private long domainId;
	private String userName;
	private String userId;
	private String employeeId;
	private String orgUnitName;
	private String orgUnitUUID;
	private boolean userActive;
	private long roleId;
	private String itSystem;
	private long systemRoleWeight; // This will contain the highest systemRole weight for this JFR
	private long itSystemResultWeight; // This will contain the highest systemRole weight for the users for the it-system
	private String assignedBy;
	private Date assignedWhen;
	private String assignedThrough;
	private String postponedConstraints = "";
	private boolean notifyByEmailIfManualSystem = true;
	private LocalDate startDate;
	private LocalDate stopDate;
}
