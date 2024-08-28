package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
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
public class AdminAttestationDetailsDTO {
    private Long id;
    private Attestation.AttestationType attestationType;
    private AttestationOverviewDTO overview;
    private List<AttestationSentMailDTO> sentEmails;
}
