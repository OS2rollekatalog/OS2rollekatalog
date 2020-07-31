package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum CheckupIntervalEnum {
	MONTHLY("html.setting.attestation.scheduled.interval.monthly"),
	QUARTERLY("html.setting.attestation.scheduled.interval.quarterly"),
	EVERY_HALF_YEAR("html.setting.attestation.scheduled.interval.every_half_year"),
	YEARLY("html.setting.attestation.scheduled.interval.yearly");
	
	private String message;
	
	private CheckupIntervalEnum(String message) {
		this.message = message;
	}
}
