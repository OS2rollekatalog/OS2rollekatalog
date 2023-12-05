package dk.digitalidentity.rc.controller.validator;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ItSystemForm;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;

@Component
public class ItSystemValidator implements Validator {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private UserService userService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (ItSystemForm.class.isAssignableFrom(aClass));
	}
	
	@Override
	public void validate(Object o, Errors errors) {
		ItSystemForm itSystemForm = (ItSystemForm) o;

		ItSystem itSystemFromDBName = itSystemService.getFirstByName(itSystemForm.getName());

		// if a itsystem exists with that name, reject (unless we are editing of course ;))
		if (itSystemFromDBName != null && itSystemFromDBName.getId() != itSystemForm.getId()) {
			errors.rejectValue("name", "html.errors.itsystem.name.unique");
		}

		ItSystem itSystemFromDBIdentifier = itSystemService.getFirstByIdentifier(itSystemForm.getIdentifier());

		// if a itsystem exists with that identifier , reject (unless we are editing of course ;))
		if (itSystemFromDBIdentifier != null && itSystemFromDBIdentifier.getId() != itSystemForm.getId()) {
			errors.rejectValue("identifier", "html.errors.itsystem.identifier.unique");
		}
		
		if (itSystemForm.getName().length() < 2) {
			errors.rejectValue("name", "html.errors.itsystem.name.notempty");
		}

		if (itSystemForm.getSystemType() == ItSystemType.MANUAL) {
			if (!StringUtils.hasLength(itSystemForm.getEmail())) {
				errors.rejectValue("email", "html.errors.itsystem.email.notempty");
			}
			
			checkEmailSyntax(errors, itSystemForm);
		}

		if (itSystemForm.getSystemType() == ItSystemType.AD || itSystemForm.getSystemType() == ItSystemType.SAML) {
			if (StringUtils.hasLength(itSystemForm.getEmail())) {
				checkEmailSyntax(errors, itSystemForm);
			}
		}

		if (itSystemForm.getIdentifier().length() < 2) {
			errors.rejectValue("identifier", "html.errors.itsystem.identifier.notempty");
		}

		if (itSystemForm.getSystemType() == ItSystemType.AD) {
			Domain domain = domainService.getByName(itSystemForm.getDomain());
			if (domain == null) {
				errors.rejectValue("domain", "html.errors.itsystem.domain.notempty");
			}
		}

		if (StringUtils.hasLength(itSystemForm.getSelectedResponsibleUuid())) {
			if (userService.getByUuid(itSystemForm.getSelectedResponsibleUuid()) == null) {
				errors.rejectValue("selectedResponsibleUuid", "html.errors.itsystem.selectedResponsibleUuid.notfound");
			}
		}
	}

	private void checkEmailSyntax(Errors errors, ItSystemForm itSystemForm) {
		if (itSystemForm.getEmail() != null) {
			String[] emails = itSystemForm.getEmail().split(";");
			for (String email : emails) {

				// Regular Expression by RFC 5322 for Email Validation
				String regex = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";

				if (!Pattern.matches(regex, email)) {
					errors.rejectValue("email", "html.errors.itsystem.email.notemail");
				}
			}
		}
	}
}
