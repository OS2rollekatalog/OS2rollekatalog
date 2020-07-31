package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.Set;

import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.model.OrganisationEventAction;
import dk.digitalidentity.rc.service.model.RequestApproveManagerAction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettingsForm {
	private boolean requestApproveEnabled;
	private boolean itSystemMarkupEnabled;
	private String servicedeskEmail;
	private String itSystemChangeEmail;
	private RequestApproveManagerAction requestApproveManagerAction;
	
	private boolean organisationEventsEnabled;
	private OrganisationEventAction ouNewManagerAction;
	private OrganisationEventAction ouNewParentAction;
	private OrganisationEventAction userNewPositionAction;
	
	private boolean scheduledAttestationEnabled;
	private CheckupIntervalEnum scheduledAttestationInterval;
	private long scheduledAttestationDayInMonth;
	private Set<String> scheduledAttestationFilter;
}