package dk.digitalidentity.rc.controller.validator;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ItSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleForm;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.service.SystemRoleService;

@Component
public class SystemRoleValidator implements Validator {

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private ItSystemService itSystemService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (SystemRoleForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		SystemRoleForm systemRoleForm = (SystemRoleForm) o;

		SystemRole systemRoleFromDB = systemRoleService.getFirstByIdentifierAndItSystemId(systemRoleForm.getIdentifier(), systemRoleForm.getItSystemId());
		ItSystem itSystem = itSystemService.getById(systemRoleForm.getItSystemId());

		// if a itsystem exists with that identifier
		if (systemRoleFromDB != null && systemRoleFromDB.getId() != systemRoleForm.getId()) {
			errors.rejectValue("identifier", "html.errors.systemrole.identifier.unique");
		}
		
		if (systemRoleForm.getIdentifier().length() < 2) {
			errors.rejectValue("identifier", "html.errors.systemrole.identifier.notempty");			
		}
		
		if (systemRoleForm.getName().length() < 2) {
			errors.rejectValue("name", "html.errors.systemrole.name.notempty");			
		}
		
		if (systemRoleForm.getWeight() < 1) {
			errors.rejectValue("weight", "html.errors.systemrole.weight.lessthanone");
		}

		if (itSystem != null && itSystem.getSystemType() == ItSystemType.AD
                && systemRoleForm.getIdentifier().matches(".*[\\\\/\\[\\]:;|=,+*?<>\"]+.*")) {
			errors.rejectValue("identifier", "html.errors.systemrole.identifier.invalid");
		}
	}
}
