package dk.digitalidentity.rc.service.dmp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DMPSetRoleAssignmentsRequest {
	private List<DMPSetRoleAssignment> userRoleAssignments;
}
