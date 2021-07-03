package dk.digitalidentity.rc.service.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleAssignmentReportEntry {
	private String userName;
	private String userId;
	private boolean userActive;
	private long roleId;
	private String itSystem;
	private String assignedBy;
	private Date assignedWhen;
	private String assignedThrough;
}
