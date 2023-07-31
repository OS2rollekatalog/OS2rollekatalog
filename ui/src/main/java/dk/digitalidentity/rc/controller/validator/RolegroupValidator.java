package dk.digitalidentity.rc.controller.validator;


import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupForm;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.service.RoleGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.thymeleaf.util.StringUtils;

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
        RoleGroup alreadyExistingRoleGroup = roleGroupService.getByName(rolegroup.getName());

        if (alreadyExistingRoleGroup != null && alreadyExistingRoleGroup.getId() != rolegroup.getId()){
            errors.rejectValue("name", "html.errors.rolegroup.name.unique");
        }

        if (StringUtils.isEmpty(rolegroup.getName())) {
            errors.rejectValue("name", "html.errors.rolegroup.name.null");
        } else if (rolegroup.getName().length() > 128) {
            errors.rejectValue("name", "html.errors.rolegroup.name.toolong");
        }
    }
}
