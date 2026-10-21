package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

public enum AttestationEscalationRecipients {
	MANAGER("html.setting.attestation.escalation.recipients.manager", true, false),
	FIXED_ADDRESSES("html.setting.attestation.escalation.recipients.fixed_addresses", false, true),
	MANAGER_AND_FIXED_ADDRESSES("html.setting.attestation.escalation.recipients.manager_and_fixed_addresses", true, true);

	@Getter
	private final String message;
	private final boolean includesManager;
	private final boolean includesFixedAddresses;

	AttestationEscalationRecipients(String message, boolean includesManager, boolean includesFixedAddresses) {
		this.message = message;
		this.includesManager = includesManager;
		this.includesFixedAddresses = includesFixedAddresses;
	}

	public boolean includesManager() {
		return includesManager;
	}

	public boolean includesFixedAddresses() {
		return includesFixedAddresses;
	}
}
