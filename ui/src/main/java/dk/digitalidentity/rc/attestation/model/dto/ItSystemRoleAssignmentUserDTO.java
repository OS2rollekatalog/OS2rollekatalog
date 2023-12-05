package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItSystemRoleAssignmentUserDTO {
	private String userUuid;
	private String userId;
	private String userName;
	private String position;
	private String verifiedByUserId;
	private String remarks;
	private boolean readOnly;
	private List<ItSystemRoleAssignmentUserRoleDTO> userRoles;
}
