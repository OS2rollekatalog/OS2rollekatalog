package dk.digitalidentity.rc.controller.validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ItSystemForm;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ItSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ItSystemValidator implements Validator {

	@Autowired
	private ItSystemService itSystemService;

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

		if (itSystemForm.getSystemType().equals(ItSystemType.MANUAL) && itSystemForm.getEmail().isEmpty()) {
			errors.rejectValue("email", "html.errors.itsystem.email.notempty");
		}
		
		if (itSystemForm.getIdentifier().length() < 2) {
			errors.rejectValue("identifier", "html.errors.itsystem.identifier.notempty");
		}
	}
}
