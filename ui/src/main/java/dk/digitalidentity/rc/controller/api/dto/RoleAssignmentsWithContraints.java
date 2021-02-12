package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAssignmentsWithContraints {
	private String extUuid;
	private String userId;
	
	private List<RoleAssignmentWithContraints> assignments;
}
