package dk.digitalidentity.rc.controller.validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationSettingsForm;
import dk.digitalidentity.rc.dao.model.enums.AttestationEscalationRecipients;
import dk.digitalidentity.rc.service.SettingsService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import java.time.LocalDate;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class AttestationSettingFormValidator implements Validator {
	private final SettingsService settingsService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (AttestationSettingsForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		AttestationSettingsForm settingsForm = (AttestationSettingsForm) o;

		defaultMissingEscalationSettings(settingsForm);
		validateEscalationEmailFormat(settingsForm, errors);

		if (settingsForm.isScheduledAttestationEnabled()) {
			validateEscalationEmailsPresent(settingsForm, errors);
			validateAttestationChangeEmail(settingsForm, errors);
			validateFirstAttestationDate(settingsForm, errors);
			validateMaxAttestationsToRender(settingsForm, errors);
		}
	}

	private static void defaultMissingEscalationSettings(AttestationSettingsForm settingsForm) {
		if (settingsForm.getEscalationRecipients() == null) {
			settingsForm.setEscalationRecipients(AttestationEscalationRecipients.MANAGER);
		}
		if (settingsForm.getEscalationEmails() == null) {
			settingsForm.setEscalationEmails(new ArrayList<>());
		}
	}

	private static void validateEscalationEmailFormat(AttestationSettingsForm settingsForm, Errors errors) {
		settingsForm.getEscalationEmails().stream()
				.filter(StringUtils::hasText)
				.map(String::trim)
				.filter(email -> !isValidEmail(email))
				.findFirst()
				.ifPresent(invalid -> errors.rejectValue("escalationEmails", "html.errors.attestation.escalation.emails.invalid", new Object[] { invalid }, null));
	}

	private static boolean isValidEmail(String email) {
		try {
			return email.equals(new InternetAddress(email, true).getAddress());
		} catch (AddressException e) {
			return false;
		}
	}

	private static void validateEscalationEmailsPresent(AttestationSettingsForm settingsForm, Errors errors) {
		if (settingsForm.getEscalationRecipients().includesFixedAddresses() && settingsForm.getEscalationEmails().stream().noneMatch(StringUtils::hasText)) {
			errors.rejectValue("escalationEmails", "html.errors.attestation.escalation.emails.empty");
		}
	}

	private static void validateAttestationChangeEmail(AttestationSettingsForm settingsForm, Errors errors) {
		if (!StringUtils.hasLength(settingsForm.getAttestationChangeEmail())) {
			errors.rejectValue("attestationChangeEmail", "html.errors.attestation.email.empty");
		}
	}

	/** A date in the past is only accepted when it is the already stored date, left unchanged. */
	private void validateFirstAttestationDate(AttestationSettingsForm settingsForm, Errors errors) {
		final LocalDate submitted = settingsForm.getFirstAttestationDate();
		if (submitted == null) {
			errors.rejectValue("firstAttestationDate", "html.errors.attestation.date");
			return;
		}
		final boolean unchanged = !settingsService.firstAttestationDateIsNull() && submitted.equals(settingsService.getFirstAttestationDate());
		if (submitted.isBefore(LocalDate.now()) && !unchanged) {
			errors.rejectValue("firstAttestationDate", "html.errors.attestation.date");
		}
	}

	private static void validateMaxAttestationsToRender(AttestationSettingsForm settingsForm, Errors errors) {
		try {
			int max = Integer.parseInt(settingsForm.getMaxAttestationsToRenderOnOverview());
			if (max <= 0) {
				errors.rejectValue("maxAttestationsToRenderOnOverview", "html.errors.attestation.maxRunsToRender.nonZero");
			}
		} catch (NumberFormatException e) {
			errors.rejectValue("maxAttestationsToRenderOnOverview", "html.errors.attestation.maxRunsToRender.numeric");
		}
	}
}
