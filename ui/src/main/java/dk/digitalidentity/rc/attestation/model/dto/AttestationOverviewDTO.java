package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttestationOverviewDTO {
    private boolean readOnly;
    private String name;
    private String id;
    private long numberAttestated;
    private long numberToAttestate;
    private long totalNumber;
    private LocalDate deadline;
    private boolean passedDeadline;
    private boolean orgUnitRolesVerified;
    private List<String> substitutes;

    // theese fields are only relevant for itSystemRoleAssignmentAttestation
    private long orgUnitNumberAttestated;
    private long orgUnitNumberToAttestate;
    private long orgUnitTotalNumber;
}
