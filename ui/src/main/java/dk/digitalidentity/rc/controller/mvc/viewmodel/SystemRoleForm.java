package dk.digitalidentity.rc.controller.mvc.viewmodel;

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
    
    // only used for system-roles matching AD groups
    private boolean createADGroup;
}
