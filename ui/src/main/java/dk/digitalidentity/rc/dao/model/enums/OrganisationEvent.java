package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum OrganisationEvent {
	OU_LEADER_CHANGED("html.enum.organisationEvent.ou_leader_changed"),
	OU_MOVED("html.enum.organisationEvent.ou_moved"),
	EMPLOYEE_POSITION_CHANGED("html.enum.organisationEvent.employee_position_changed"),
	EMPLOYEE_OU_RELATION_ADDED("html.enum.organisationEvent.employee_ou_relation_added"),
	EMPLOYEE_OU_RELATION_REMOVED("html.enum.organisationEvent.employee_ou_relation_removed"),
	EMPLOYEE_OU_RELATION_COMBO("html.enum.organisationEvent.employee_ou_relation_combo"),
	EMPLOYEE_LEADER_CHANGED("html.enum.organisationEvent.employee_leader_changed");

	private String message;

	private OrganisationEvent(String message) {
		this.message = message;
	}
}
