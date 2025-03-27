package dk.digitalidentity.rc.attestation.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ADAttestationUserDTO {
	private String uuid;
	private String name;
	private String username;
	private String responsibleOU;
	private String responsibleUser;
	private LocalDate verifiedAt;
}
