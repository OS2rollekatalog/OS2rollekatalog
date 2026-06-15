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
	MAX_COUNT_PLACEHOLDER("{maksimum}", "html.enum.placeholders.description.max"),
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
	REQUEST_REASON("{begrundelse}", "html.enum.placeholders.description.request_reason"),
	ATTESTATION_CHANGES_OU("{ændringer_enhed}", "html.enum.placeholders.description.attestation_changes_ou"),
	ATTESTATION_CHANGES_USERS("{ændringer_brugere}", "html.enum.placeholders.description.attestation_changes_users"),
	START_DATE("{startdato}", "html.enum.placeholders.description.start_date"),
	STOP_DATE("{stopdato}", "html.enum.placeholders.description.stop_date"),
	REQUESTER_TYPE_PLACEHOLDER("{anmoderType}", "html.enum.placeholders.description.requester_type"),
	USER_ID_PLACEHOLDER("{brugernavn}", "html.enum.placeholders.description.user_id"),
	PERSON_UUID_PLACEHOLDER("{PersonUuid}", "html.enum.placeholders.description.person_uuid"),
	USERS_BLOCK_PLACEHOLDER("{brugere}", "html.enum.placeholders.description.users_block"),
	CHANGES_PLACEHOLDER("{ændringer}", "html.enum.placeholders.description.changes"),
	ACTION_PLACEHOLDER("{handling}", "html.enum.placeholders.description.action"),
	ACTION_PAST_PLACEHOLDER("{handlet}", "html.enum.placeholders.description.action_past"),
	ASSIGNED_BY_PLACEHOLDER("{tildeler}", "html.enum.placeholders.description.assigned_by"),
	ROLE_DESCRIPTION_PLACEHOLDER("{rollebeskrivelse}", "html.enum.placeholders.description.role_description", true);

	private final String placeholder;
	private final String description;
	// parameterized placeholders also accept {navn:N} where N truncates the value to the first line and at most N characters
	private final boolean parameterized;

	EmailTemplatePlaceholder(String placeholder, String description) {
		this(placeholder, description, false);
	}

	EmailTemplatePlaceholder(String placeholder, String description, boolean parameterized) {
		this.placeholder = placeholder;
		this.description = description;
		this.parameterized = parameterized;
	}
}
