package dk.digitalidentity.rc.dao.model.assignment.hook;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.service.assignment.mapper.HistoricAssignmentMapper;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HookEvent {
	public enum HookAction { ADD, REMOVE }
	
	private HookAction action;

	// identify user / recipient of role
	@NotNull
	private String userUuid;
	@NotNull
	private String userId;
	
	// identify role
	@NotNull
	private long userRoleId;
	@NotNull
	private String userRoleName;
	
	// identify it-system
	@NotNull
	private long itSystemId;
	@NotNull
	private String itSystemName;
	
	// identify assignmentType (for printing on screen, not direct references)
	@NotNull
	private AssignedThrough assignedThrough;
	private String assignedThroughOuName;
	private String assignedThroughTitleName;
	private String assignedThroughRoleGroupName;

	public HookEvent(CurrentAssignment assignment) {
		this(HistoricAssignmentMapper.createFromCurrentAssignment(assignment, null));
	}

	public HookEvent(HistoricAssignment assignment) {
		this.action = (assignment.getValidTo() == null) ? HookAction.ADD : HookAction.REMOVE;
			
		this.userUuid = assignment.getUserUuid();
		this.userId = assignment.getUserId();
		
		this.userRoleId = assignment.getUserRoleId();
		this.userRoleName = assignment.getUserRoleName();
		
		this.itSystemId = assignment.getItSystemId();
		this.itSystemName = assignment.getItSystemName();
		
		this.assignedThrough = assignment.getAssignedThroughType();
		this.assignedThroughOuName = assignment.getAssignedThroughOUName();
		this.assignedThroughTitleName = assignment.getAssignedThroughTitleName();
		this.assignedThroughRoleGroupName = assignment.getAssignedThroughRoleGroupName();
	}
}
