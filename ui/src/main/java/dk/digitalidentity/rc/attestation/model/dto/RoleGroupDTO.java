package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dk.digitalidentity.rc.attestation.model.dto.enums.AssignedThroughAttestation;
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
public class RoleGroupDTO {
    private String groupName;
    private Long groupId;
    private String groupDescription;
    private List<UserRoleDTO> userRoles;
    private boolean inherited;
    private AssignedThroughAttestation assignedThrough;
    private String assignedThroughName; // not always relevant - can be null
    private String responsible; // only relevant for doNotVerify roles - can be null.
}
