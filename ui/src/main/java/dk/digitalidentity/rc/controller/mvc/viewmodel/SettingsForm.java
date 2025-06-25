package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class SettingsForm {
	private boolean requestApproveEnabled;
	private String servicedeskEmail;
	private String itSystemChangeEmail;
	private boolean caseNumberEnabled;
	private Set<String> excludedOUs;
}