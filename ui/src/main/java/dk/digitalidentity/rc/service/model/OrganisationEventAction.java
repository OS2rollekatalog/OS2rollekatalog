package dk.digitalidentity.rc.service.model;

import lombok.Getter;

@Getter
public enum OrganisationEventAction {
	RIGHTS_KEPT("html.enum.organisationEventAction.rights_kept"),
	RIGHTS_REMOVED("html.enum.organisationEventAction.rights_removed"),
	RIGHTS_NEEDS_APPROVAL("html.enum.organisationEventAction.rights_needs_approval"),
	DIRECT_RIGHTS_REMOVED("html.enum.organisationEventAction.direct_rights_removed");

	private String message;

	private OrganisationEventAction(String message) {
		this.message = message;
	}
}
