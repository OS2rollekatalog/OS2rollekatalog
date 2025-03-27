package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dk.digitalidentity.rc.attestation.model.dto.enums.AssignedThroughAttestation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItSystemRoleAssignmentUserRoleDTO {
	private long roleId;
	private String roleName;
	private String roleDescription;
	private AssignedThroughAttestation assignedThrough;
	private String assignedThroughName; // can be null
	private String verifiedByUserId;
	private String remarks;
	private String postponedConstraints;
}
