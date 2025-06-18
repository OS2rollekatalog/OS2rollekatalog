package dk.digitalidentity.rc.rolerequest.model.dto;

import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.ReasonOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class RolerequestSettingsDTO {
	private ReasonOption reasonSetting;
	private ApproverOption approverSetting;
	private RequesterOption requesterSetting;
	private Set<RequestConstraintDTO> constraints = new HashSet<>();
	private boolean onlyRecommendRoles;
}
