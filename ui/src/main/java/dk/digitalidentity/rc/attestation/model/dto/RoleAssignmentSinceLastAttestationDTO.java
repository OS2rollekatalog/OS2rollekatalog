package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dk.digitalidentity.rc.attestation.model.dto.enums.AssignedThroughAttestation;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleAssignmentSinceLastAttestationDTO {
    private long roleId;
    private String roleName;
    private String userUuid;
    private String userId;
    private String userName;
    private LocalDate assignedFrom;
    private LocalDate assignedTo;
    private AssignedThroughAttestation assignedThrough;
    private String assignedThroughName; // not always relevant - can be null
    private RoleType roleType;
}
