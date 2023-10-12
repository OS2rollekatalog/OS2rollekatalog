package dk.digitalidentity.rc.controller.validator;

import dk.digitalidentity.rc.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;

import java.time.LocalDate;

@Component
public class SettingFormValidator implements Validator {

	@Autowired
	private SettingsService settingsService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (SettingsForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		SettingsForm settingsForm = (SettingsForm) o;

		if (settingsForm.isScheduledAttestationEnabled()) {
			if (!StringUtils.hasLength(settingsForm.getAttestationChangeEmail())) {
				errors.rejectValue("attestationChangeEmail", "html.errors.attestation.email.empty");
			}

			if (settingsForm.getFirstAttestationDate() == null) {
				errors.rejectValue("firstAttestationDate", "html.errors.attestation.date");
			} else {
				if (settingsService.firstAttestationDateIsNull()) {
					if (settingsForm.getFirstAttestationDate().isBefore(LocalDate.now())) {
						errors.rejectValue("firstAttestationDate", "html.errors.attestation.date");
					}
				} else {
					if ((!settingsService.getFirstAttestationDate().isBefore(LocalDate.now()) && settingsForm.getFirstAttestationDate().isBefore(LocalDate.now()))
							|| (settingsForm.getFirstAttestationDate().isBefore(LocalDate.now()) && !settingsService.getFirstAttestationDate().equals(settingsForm.getFirstAttestationDate()))) {
						errors.rejectValue("firstAttestationDate", "html.errors.attestation.date");
					}
				}
			}
		}
	}
}
