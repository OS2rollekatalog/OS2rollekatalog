package dk.digitalidentity.rc.attestation.model.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttestationSentMailReceiverDTO {
    private boolean cc;
    private String userName;
    private String email;
    private String title;
    private String message;
}
