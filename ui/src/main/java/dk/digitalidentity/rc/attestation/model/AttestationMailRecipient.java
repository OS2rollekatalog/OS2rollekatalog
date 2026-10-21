package dk.digitalidentity.rc.attestation.model;

import java.util.Objects;

import dk.digitalidentity.rc.attestation.model.entity.AttestationMailReceiver;
import dk.digitalidentity.rc.dao.model.User;

/** A receiver of mails for Attestation. Can either be a user of an email mailbox*/
public record AttestationMailRecipient(String userUuid, String name, String email) {
	private static final String FIXED_ADDRESS_NAME = "modtager";

	public static AttestationMailRecipient forUser(final User user) {
		return new AttestationMailRecipient(user.getUuid(), user.getName(), user.getEmail());
	}

	public static AttestationMailRecipient forAddress(final String email) {
		return new AttestationMailRecipient(null, FIXED_ADDRESS_NAME, email);
	}

	public boolean isUser() {
		return userUuid != null;
	}

	public boolean matches(final AttestationMailReceiver receiver) {
		if (isUser()) {
			return Objects.equals(userUuid, receiver.getUserUuid());
		}
		return receiver.getUserUuid() == null && email.equalsIgnoreCase(receiver.getEmail());
	}
}
