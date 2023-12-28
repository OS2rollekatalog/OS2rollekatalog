package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum EventType {
	LOGIN_LOCAL(""),      // login to Role Catalogue
	LOGIN_EXTERNAL(""),   // login to some external system (i.e. SAML lookup from AD FS or similar)

	CREATE(""),
	DELETE(""),

	ASSIGN_USER_ROLE("html.enum.eventtype.assign_user_role"),
	REMOVE_USER_ROLE("html.enum.eventtype.remove_user_role"),
	EDIT_USER_ROLE_ASSIGNMENT("html.enum.eventtype.edit_user_role_assignment"),
	
	ASSIGN_ROLE_GROUP("html.enum.eventtype.assign_role_group"),
	REMOVE_ROLE_GROUP("html.enum.eventtype.remove_role_group"),
	EDIT_ROLE_GROUP_ASSIGNMENT("html.enum.eventtype.edit_role_group_assignment"),
	
	ASSIGN_SYSTEMROLE("html.enum.eventtype.assign_systemrole"),
	REMOVE_SYSTEMROLE("html.enum.eventtype.remove_systemrole"),
	
	ASSIGN_KLE("html.enum.eventtype.assign_kle"),
	REMOVE_KLE("html.enum.eventtype.remove_kle"),
	
	ATTESTED_ORGUNIT("html.enum.eventtype.attested"),
	
	AUTH_MANAGER_ADDED("html.enum.eventtype.add_auth_manager"),
	AUTH_MANAGER_REMOVED("html.enum.eventtype.remove_auth_manager"),
	REQUEST_ROLE_FOR("html.enum.eventtype.request"),
	REQUEST_ROLE_REMOVAL_FOR("html.enum.eventtype.removal_request"),
	APPROVE_REQUEST("html.enum.eventtype.approve_request"),
	REJECT_REQUEST("html.enum.eventtype.reject_request"),
	
	PERFORMED_USERROLE_CLEANUP("html.enum.eventtype.cleanup.userroles"),
	PERFORMED_ROLEGROUP_CLEANUP("html.enum.eventtype.cleanup.rolegroups"),
	
	ADMIN_ASSIGNED_MANAGER_SUBSTITUTE("html.enum.eventtype.admin_assigned_manager_substitute");

	private String message;
	
	private EventType(String message) {
		this.message = message;
	}
}
