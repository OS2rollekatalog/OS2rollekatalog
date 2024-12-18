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
public class OrgUnitUserRoleAssignmentDTO {
    private long roleId;
    private String roleName;
    private String roleDescription;
    private List<ExceptedUserDTO> exceptedUsers;
    private List<String> titles;
    private boolean inherit;
    private List<String> exceptedTitles;
    private String postponedConstraints; // never set
}
