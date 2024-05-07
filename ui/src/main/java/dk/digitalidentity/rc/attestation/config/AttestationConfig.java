package dk.digitalidentity.rc.attestation.config;

import lombok.Data;

@Data
public class AttestationConfig {

    private Boolean alwaysRunTracker = false;
    private Integer daysForAttestation = 30;
    private Integer notifyDaysBeforeDeadline = 20;
    private Integer reminder1DaysBeforeDeadline = 10;
    private Integer reminder2DaysBeforeDeadline = 3;
    private Integer reminder3DaysAfterDeadline = 5;
    private Integer escalationReminderDaysAfterDeadline = 5;

}
