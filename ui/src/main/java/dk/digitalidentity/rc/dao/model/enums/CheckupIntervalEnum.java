package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum CheckupIntervalEnum {
	EVERY_HALF_YEAR("html.setting.attestation.scheduled.interval.every_half_year"),
	YEARLY("html.setting.attestation.scheduled.interval.yearly");
	
	private String message;
	
	private CheckupIntervalEnum(String message) {
		this.message = message;
	}
}
