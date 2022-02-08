package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.Set;

import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.model.WhoCanRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettingsForm {
	private boolean requestApproveEnabled;
	private String servicedeskEmail;
	private String itSystemChangeEmail;
	private String removalOfUnitRolesEmail;
	private WhoCanRequest requestApproveWho;
	
	private boolean scheduledAttestationEnabled;
	private CheckupIntervalEnum scheduledAttestationInterval;
	private CheckupIntervalEnum scheduledAttestationIntervalSensitive;
	private long scheduledAttestationDayInMonth;
	private Set<String> scheduledAttestationFilter;
	private String emailAttestationReport;
	
	private int reminderCount;
	private int reminderInterval;
	private int daysBeforeDeadline;
	private String emailAfterReminders;
	
	private boolean attestationRoleDeletionEnabled;
	
	private boolean adAttestationEnabled;
}