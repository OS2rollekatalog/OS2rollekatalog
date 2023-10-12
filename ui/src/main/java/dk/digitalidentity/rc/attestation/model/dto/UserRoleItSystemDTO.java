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
public class UserRoleItSystemDTO {
    private long itSystemId;
    private String itSystemName;
    private List<UserRoleDTO> userRoles;
}
