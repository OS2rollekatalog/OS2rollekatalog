package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class AttestationSettingsForm {
	private boolean scheduledAttestationEnabled;
	private CheckupIntervalEnum scheduledAttestationInterval;
	private Set<String> scheduledAttestationFilter;
	private boolean dontSendMailToManager;
	private boolean adAttestationEnabled;
	private boolean changeRequestsEnabled;
	private String attestationChangeEmail;
	private boolean descriptionRequired;
	private boolean hideDescription;
	private boolean orgUnitOptIn;
	private Set<String> scheduledAttestationOptedInOrgUnits = new HashSet<>();

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate firstAttestationDate;
}