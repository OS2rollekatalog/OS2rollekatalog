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
public class UserAttestationDTO {
    private String userUuid;
    private String userId;
    private String userName;
    private String position;
    private String verifiedByUserId;
    private String remarks;
    private boolean adRemoval;
    private boolean manager;
    private boolean readOnly;
    private List<RoleGroupDTO> roleGroups;
    private List<UserRoleItSystemDTO> userRolesPrItSystem;
    private List<RoleGroupDTO> doNotVerifyRoleGroups;
    private List<UserRoleItSystemDTO> doNotVerifyUserRolesPrItSystem;
    private boolean isPrimary;
}
