package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManagerDelegateOrganisationAttestationDTO {
    private LocalDate createdAt;
    private String attestationUuid;
    private String ouUuid;
    private String ouName;
    private LocalDate deadLine;
    private List<RoleAssignmentSinceLastAttestationDTO> roleAssignmentsSinceLastAttestation;
    private List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRoleAssignmentsPrItSystem;  // all the userRoles assigned directly to this orgUnit
    private List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroupAssignments; // all the roleGroups assigned directly to this orgUnit
    private boolean orgUnitRolesVerified;
    private List<UserAttestationDTO> userAttestations;
    @Builder.Default
    private Set<String> associatedManagerNames = new HashSet<>();
}
