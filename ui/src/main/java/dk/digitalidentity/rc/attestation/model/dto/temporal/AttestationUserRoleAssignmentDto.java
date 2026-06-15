package dk.digitalidentity.rc.attestation.model.dto.temporal;

import java.io.Serializable;
import java.time.LocalDate;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import lombok.Value;

/**
 * DTO for {@link AttestationUserRoleAssignment}
 */
@Value
public class AttestationUserRoleAssignmentDto implements Serializable {
	private static final long serialVersionUID = 7394360164654522220L;

	private LocalDate validFrom;
	private LocalDate validTo;
	private LocalDate updatedAt;
	private String recordHash;
	private String userUuid;
	private String userId;
	private String userName;
	private long userRoleId;
	private String userRoleName;
	private String userRoleDescription;
	private Long roleGroupId;
	private String roleGroupName;
	private String roleGroupDescription;
	private Long itSystemId;
	private String itSystemName;
	private Long responsibleCollectionId;
	private String responsibleOuName;
	private String roleOuUuid;
	private String roleOuName;
	private String responsibleOuUuid;
	private AssignedThroughType assignedThroughType;
	private String assignedThroughName;
	private String assignedThroughUuid;
	private boolean inherited;
	private boolean sensitiveRole;
	private boolean extraSensitiveRole;
	private LocalDate assignedFrom;
	private String postponedConstraints;
}
