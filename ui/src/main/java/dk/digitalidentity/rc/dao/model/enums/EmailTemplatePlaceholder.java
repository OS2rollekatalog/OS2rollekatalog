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
	ATTESTATION_DEADLINE("{deadline}", "html.enum.placeholders.description.deadline"),
	REQUEST_OPERATION_PLACEHOLDER("{operation}", "html.enum.placeholders.description.operation"),
	NEW_POSITIONS_PLACEHOLDER("{ny_stillinger}", "html.enum.placeholders.description.new_positions"),
	OLD_POSITIONS_PLACEHOLDER("{tidligere_stillinger}", "html.enum.placeholders.description.old_positions"),
	ORGUNITS_PLACEHOLDER("{enheder}", "html.enum.placeholders.description.orgunits"),
	MANAGERDELEGATE_PLACEHOLDER("{delegeret_fra}", "html.enum.placeholders.description.managerdelegate"),
	;

	private final String placeholder;
	private final String description;
	
	EmailTemplatePlaceholder(String placeholder, String description) {
		this.placeholder = placeholder;
		this.description = description;
	}
}
