package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dk.digitalidentity.rc.attestation.model.dto.enums.AdminAttestationStatus;
import dk.digitalidentity.rc.dao.model.User;
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
public class AttestationStatusListDTO {
    private Long id;
    private String name;
    private User manager;
    private List<User> substitutes;
    private User responsibleUser;
    private String path;
    private AdminAttestationStatus status;
}
