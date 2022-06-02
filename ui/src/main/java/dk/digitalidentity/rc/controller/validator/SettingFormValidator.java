package dk.digitalidentity.rc.controller.validator;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;

@Component
public class SettingFormValidator implements Validator {

	@Override
	public boolean supports(Class<?> aClass) {
		return (SettingsForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		SettingsForm settingsForm = (SettingsForm) o;

		if (settingsForm.isScheduledAttestationEnabled()) {
			if (!StringUtils.hasLength(settingsForm.getEmailAttestationReport())) {
				errors.rejectValue("emailAttestationReport", "html.errors.attestation.email.empty");
			}
		}
	}
}
