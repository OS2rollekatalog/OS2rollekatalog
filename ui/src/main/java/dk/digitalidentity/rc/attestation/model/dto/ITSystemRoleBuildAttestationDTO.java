package dk.digitalidentity.rc.attestation.model.dto;

import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
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
public class ITSystemRoleBuildAttestationDTO {
	private String itSystemName;
	private String role;
	private List<String> systemRole;
	private String responsibleUser;
	private AttestationStatus attestationStatus = AttestationStatus.NOT_VERIFIED;
	private LocalDate attestationDate;
	private String performedBy;
}
