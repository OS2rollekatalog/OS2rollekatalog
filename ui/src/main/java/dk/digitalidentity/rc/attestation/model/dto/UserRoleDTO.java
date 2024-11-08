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
public class UserRoleDTO {
    private String roleName;
    private String roleIdentifier;
    private long roleId;
    private String roleDescription;
    private String itSystemName;
    private Boolean inherited;
    private AssignedThroughAttestation assignedThrough; // can be null if this object is a part of the list on the UserRoleGroupDto
    private String assignedThroughName; // not always relevant - can be null
    private String responsible; // only relevant for doNotVerify roles - can be null.
    private String postponedConstraints;
}