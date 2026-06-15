package dk.digitalidentity.rc.attestation.model.dto;

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
public class ADAttestationUserDTO {
	private String uuid;
	private String name;
	private String username;
	private String responsibleOU;
	private List<String> responsibleUserNames;
	private LocalDate verifiedAt;
}
