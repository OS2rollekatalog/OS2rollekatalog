package dk.digitalidentity.rc.attestation.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleAssignmentReportRowDTO {
	private String userName;
	private String userUserId;
	private String position;
	private String orgUnit;
	private String userRoleName;
	private String postponedConstraints;
	private Long itSystemId;
	private String itSystemName;
	private String roleGroupName;
	private RoleStatus status;
	private LocalDate assignedFrom;
	private LocalDate assignedTo;
	private LocalDate originallyAssignedFrom;
	private String assignedThrough;
	private String assignedThroughType;
	private String responsibleUser;
	private String responsibleOu;
	private boolean inherited;
	private AttestationStatus attestationStatus;
	private LocalDate verifiedAt;
	private String verifiedByName;
	private String verifiedByUserId;
	private String remark;
	private LocalDate attestationCreatedAt;
	private LocalDate validTo;
}
