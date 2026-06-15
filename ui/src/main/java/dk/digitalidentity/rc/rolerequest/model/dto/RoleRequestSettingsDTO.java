package dk.digitalidentity.rc.rolerequest.model.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.ReasonOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleRequestSettingsDTO {
	private ReasonOption reasonSetting;
	private String servicedeskEmail;
	private List<ApprovableBy> approvableByList;
	private List<RequestableBy> requestableByList;
	private Set<RequestConstraintDTO> constraints = new HashSet<>();
	private boolean onlyRecommendRoles;
	private Map<ApprovableBy, String> alternativeEmails;
	private boolean showSingleTableInRequestApproveEnabled;
	private boolean allowSelfApproval;
}
