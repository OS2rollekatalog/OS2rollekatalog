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
public class AttestationRunDTO {
    private Long id;
    private LocalDate createdAt;
    private LocalDate deadline;
    private boolean sensitive;
    private long totalAttestations;
    private long finishedAttestations;
    private boolean superSensitive;
    private boolean finished;
    private List<AttestationStatusListDTO> ouStatus;
    private List<AttestationStatusListDTO> systemStatus;
}
