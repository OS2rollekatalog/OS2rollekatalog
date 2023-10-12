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
public class UserRoleAttestationDTO {
    private String roleName;
    private String roleIdentifier;
    private Long roleId;
    private String roleDescription;
    private String verifiedByUserId;
    private String remarks;
    private List<SystemRoleDTO> systemRoles;

}
