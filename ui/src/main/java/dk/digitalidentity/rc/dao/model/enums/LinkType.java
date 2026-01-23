package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum LinkType {
	FRONT_PAGE_LINK("Forside link"),
	GENERAL_PAGE_LINK("Genvej");

	private String message;

	private LinkType(String message) {
		this.message = message;
	}
}
