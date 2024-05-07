package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettingsForm {
	private boolean requestApproveEnabled;
	private String servicedeskEmail;
	private String itSystemChangeEmail;
}