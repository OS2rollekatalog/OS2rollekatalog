package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import javax.validation.constraints.Size;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleForm {
    private long id;
    private String identifier;
    private String uuid;
    private String delegatedFromCvr;
	private boolean userOnly;
	private boolean ouInheritAllowed;
	private boolean canRequest;
    private ItSystem itSystem;
    private List<SystemRoleAssignment> systemRoleAssignments;
    private boolean pendingSync;

    @Size(max=4000)
    private String description;

    @Size(min=5, max=64, message="{validation.role.name}")
    private String name;

    public UserRoleForm() {
    	
    }

    public UserRoleForm(UserRole userRole, boolean pendingSync) {
    	this.id = userRole.getId();
    	this.name = userRole.getName();
    	this.identifier = userRole.getIdentifier();
    	this.userOnly = userRole.isUserOnly();
    	this.uuid = userRole.getUuid();
    	this.ouInheritAllowed = userRole.isOuInheritAllowed();
    	this.canRequest = userRole.isCanRequest();
    	this.description = userRole.getDescription();
    	this.itSystem = userRole.getItSystem();
    	this.systemRoleAssignments = userRole.getSystemRoleAssignments();
    	this.delegatedFromCvr = userRole.getDelegatedFromCvr();
    	this.pendingSync = pendingSync;
    }
}
