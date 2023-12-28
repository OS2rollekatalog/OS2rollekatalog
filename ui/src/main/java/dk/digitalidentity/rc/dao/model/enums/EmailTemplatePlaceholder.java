package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum EmailTemplatePlaceholder {
	RECEIVER_PLACEHOLDER("{modtager}", "html.enum.placeholders.description.receiver"),
	ORGUNIT_PLACEHOLDER("{enhed}", "html.enum.placeholders.description.orgunit"),
	MANAGER_PLACEHOLDER("{leder}", "html.enum.placeholders.description.manager"),
	ROLE_NAME("{rolle}", "html.enum.placeholders.description.role_name"),
	USER_PLACEHOLDER("{bruger}", "html.enum.placeholders.description.user"),
	SYSTEM_RESPONSIBLE_PLACEHOLDER("{systemansvarlig}", "html.enum.placeholders.description.system_responsible"),
	COUNT_PLACEHOLDER("{antal}", "html.enum.placeholders.description.count"),
	ITSYSTEM_PLACEHOLDER("{itsystem}", "html.enum.placeholders.description.role_itsystem"),
	REQUESTER_PLACEHOLDER("{anmoder}", "html.enum.placeholders.description.requester"),
	CHANGE_REQUESTED_PLACEHOLDER("{ændring}", "html.enum.placeholders.description.change"),
	LIST_OF_CHANGE_REQUESTS("{ændringsønsker}", "html.enum.placeholders.description.changeList"),
	ATTESTATION_DEADLINE("{deadline}", "html.enum.placeholders.description.deadline");

	private String placeholder;
	private String description;
	
	private EmailTemplatePlaceholder(String placeholder, String description) {
		this.placeholder = placeholder;
		this.description = description;
	}
}
