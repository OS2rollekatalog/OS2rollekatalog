package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class SettingsForm {
	private boolean requestApproveEnabled;
	private String servicedeskEmail;
	private String itSystemChangeEmail;
	private boolean caseNumberEnabled;
	private boolean autoNiveauEnabled;
	private Set<String> excludedOUs;
	private Map<Integer, OrgUnitLevel> depthToNiveauMappings = new HashMap<>();
}