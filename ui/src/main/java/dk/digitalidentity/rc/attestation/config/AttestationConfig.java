package dk.digitalidentity.rc.attestation.config;

import lombok.Data;

@Data
public class AttestationConfig {

    private Integer daysForAttestation = 30;
    private Integer notifyDaysBeforeDeadline = 20;
    private Integer reminder1DaysBeforeDeadline = 10;
    private Integer reminder2DaysBeforeDeadline = 3;
    private Integer escalationReminderDaysAfterDeadline = 5;

}
