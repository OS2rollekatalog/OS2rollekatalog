package dk.digitalidentity.rc.controller.validator;


import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupForm;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.service.RoleGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class RolegroupValidator  implements Validator {

    @Autowired
    private RoleGroupService roleGroupService;

    @Override
    public boolean supports(Class<?> aClass) {
        return (RoleGroupForm.class.isAssignableFrom(aClass));
    }

    @Override
    public void validate(Object o, Errors errors) {
        RoleGroupForm rolegroup = (RoleGroupForm) o;
        RoleGroup alreadyExistingUser = roleGroupService.getByName(rolegroup.getName());

        if (alreadyExistingUser != null && alreadyExistingUser.getId() != rolegroup.getId()){
            errors.rejectValue("name", "html.errors.rolegroup.name.unique");
        }
    }
}
