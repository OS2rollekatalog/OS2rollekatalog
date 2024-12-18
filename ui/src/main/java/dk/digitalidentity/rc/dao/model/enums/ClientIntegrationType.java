package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum ClientIntegrationType {

	GENERIC("html.enum.clientIntegrationType.generic"),
	AD_SYNC_SERVICE("html.enum.clientIntegrationType.adSyncService");

	private String message;

	ClientIntegrationType(String message) {
		this.message = message;
	}
}
