package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.time.LocalDate;
import java.util.Set;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class SettingsForm {
	private boolean requestApproveEnabled;
	private String servicedeskEmail;
	private String itSystemChangeEmail;

	private boolean scheduledAttestationEnabled;
	private CheckupIntervalEnum scheduledAttestationInterval;
	private Set<String> scheduledAttestationFilter;
	private boolean adAttestationEnabled;
	private String attestationChangeEmail;

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate firstAttestationDate;
}