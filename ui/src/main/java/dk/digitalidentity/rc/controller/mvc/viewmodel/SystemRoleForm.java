package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemRoleForm {
    private long id;
    private String name;
    private String identifier;
    private String description;
    private long itSystemId;
    private boolean universal;
    
    // only used for system-roles matching AD groups
    private ADGroupType adGroupType;
    
    // only used for saml and AD
    private int weight = 1;
}
