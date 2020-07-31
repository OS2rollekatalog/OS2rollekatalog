package dk.digitalidentity.rc.controller.validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleForm;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class UserRoleValidator implements Validator {

	@Autowired
	private UserRoleService userRoleService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (UserRoleForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		UserRoleForm userRoleForm = (UserRoleForm) o;

		UserRole userRoleFromDB = userRoleService.getByNameAndItSystem(userRoleForm.getName(), userRoleForm.getItSystem());

		// if a role exists with that name from the same it-system, reject (unless we are editing of course ;))
		if (userRoleFromDB != null && userRoleFromDB.getId() != userRoleForm.getId()) {
			errors.rejectValue("name", "html.errors.rolegroup.name.unique");
		}
	}
}
