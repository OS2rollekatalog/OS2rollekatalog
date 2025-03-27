package dk.digitalidentity.rc.attestation.model.dto.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * DTO for {@link AttestationUserRoleAssignment}
 */
@Value
public class AttestationUserRoleAssignmentDto implements Serializable {
    LocalDate validFrom;
    LocalDate validTo;
    LocalDate updatedAt;
    String recordHash;
    String userUuid;
    String userId;
    String userName;
    long userRoleId;
    String userRoleName;
    String userRoleDescription;
    Long roleGroupId;
    String roleGroupName;
    String roleGroupDescription;
    Long itSystemId;
    String itSystemName;
    String responsibleUserUuid;
    String responsibleOuName;
    String roleOuUuid;
    String roleOuName;
    String responsibleOuUuid;
    boolean manager;
    AssignedThroughType assignedThroughType;
    String assignedThroughName;
    String assignedThroughUuid;
    boolean inherited;
    boolean sensitiveRole;
    LocalDate assignedFrom;
}