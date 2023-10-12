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
public class OrgUnitRoleGroupAssignmentDTO {
    private long groupId;
    private String groupName;
    private String groupDescription;
    private List<ExceptedUserDTO> exceptedUsers;
    private List<String> titles;
    private List<OrgUnitUserRoleAssignmentDTO> userRoles;
    private boolean inherit;
}
