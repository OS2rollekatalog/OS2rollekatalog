package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum NemLoginConstraintType {
	NONE("html.enum.nemLoginConstraint.type.none"),
	PNR("html.enum.nemLoginConstraint.type.pnr"),
	SENR("html.enum.nemLoginConstraint.type.senr");
	
	private String message;

	private NemLoginConstraintType(String message) {
		this.message = message;
	}
}
