package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum EmailTemplateType {
	REMOVE_UNIT_ROLES("html.enum.email.message.type.delete_unit_roles"),
	ATTESTATION_DOCUMENTATION("html.enum.email.message.type.attestation_documentation"),
	ATTESTATION_NOTIFICATION("html.enum.email.message.type.attestation_notification"),
	ATTESTATION_REMINDER("html.enum.email.message.type.attestation_reminder"),
	ATTESTATION_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_reminder_thirdparty"),
	SUBSTITUTE("html.enum.email.message.type.substitute"),
	ATTESTATION_EMPLOYEE_NEW_UNIT("html.enum.email.message.type.attestation_employee_new_unit"),
	ROLE_EXPIRING("html.enum.email.message.type.role_expiring");
	
	private String message;
	
	private EmailTemplateType(String message) {
		this.message = message;
	}
}