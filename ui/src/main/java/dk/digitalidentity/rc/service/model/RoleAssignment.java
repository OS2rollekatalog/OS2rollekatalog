package dk.digitalidentity.rc.service.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAssignment {
	private static final String ORGUNIT_PREFIX = "Tildelt via enhed: ";
	private static final String POSITION_PREFIX = "Tildelt via stilling: ";

	private String userUuid;
	private String assignedBy;
	private String assignedThrough;
	private Date assignedTime;
	private long userRoleId;

	public RoleAssignment(AssignedByWhen assignedByWhen, String userUuid, String assignedThroughOrgUnit, Date createdTime) {
		this.userUuid = userUuid;
		this.userRoleId = assignedByWhen.getRoleId();
		this.assignedTime = assignedByWhen.getAssignedTime();
		this.assignedThrough = ORGUNIT_PREFIX + assignedThroughOrgUnit;
		this.assignedBy = assignedByWhen.getAssignedBy();
		
		if (createdTime.after(assignedTime)) {
			this.assignedTime = createdTime;
		}
	}

	public RoleAssignment(ResultSet rs) throws SQLException {
		this(rs, true);
	}

	public RoleAssignment(ResultSet rs, boolean parseAll) throws SQLException {
		userUuid = rs.getString("user_uuid");

		if (parseAll) {
			userRoleId = rs.getInt("role_id");

			String username = rs.getString("assigned_by_name");
			String userId = rs.getString("assigned_by_user_id");
			
			assignedBy = username + " (" + userId + ")";
			
			assignedTime = rs.getDate("assigned_timestamp");
		}
		
		// optional, not always present
		try {
			Date created = rs.getDate("created_timestamp");
			
			// if the user was employeed in the OU _after_ the assignment, then we use the timestamp on the position instead
			if (created.after(assignedTime)) {
				assignedTime = created;
			}
		}
		catch (Exception ex) {
			; // ignore
		}
		
		// optional, not always present
		try {
			String orgUnitName = rs.getString("orgunit_name");
			
			if (StringUtils.hasLength(orgUnitName)) {
				assignedThrough = ORGUNIT_PREFIX + orgUnitName;
			}
		}
		catch (Exception ex) {
			;
		}
		
		// optional, not always present
		try {
			String positionName = rs.getString("position_name");
			
			if (StringUtils.hasLength(positionName)) {
				assignedThrough = POSITION_PREFIX + positionName;
			}
		}
		catch (Exception ex) {
			;
		}
	}
}
