package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserRoleForm {
    private long id;
    private String identifier;
    private String uuid;
    private String delegatedFromCvr;
	private boolean userOnly;
	private boolean canRequest;
	private boolean sensitiveRole;
	private boolean extraSensitiveRole;
    private ItSystem itSystem;
    private List<SystemRoleAssignment> systemRoleAssignments;
    private List<RoleGroup> roleGroups;
    private boolean pendingSync;
    private boolean syncFailed;
    private SystemRole linkedSystemRole;
    private boolean allowPostponing;
    private boolean requireManagerAction;
    private boolean sendToSubstitutes;
    private boolean sendToAuthorizationManagers;
    private String emailTemplateTitle = "Der er tildelt en rolle der kræver leder-involvering";
	private String emailTemplateMessage = "Kære {modtager}\n<br/>\n<br/>\nRollen {rolle}, der kræver leder-involvering, er tildelt til {bruger}.";
	private boolean roleAssignmentAttestationByAttestationResponsible;

	@Size(max = 4000)
	private String description;

	@Size(min = 5, max = 64, message = "{validation.role.name}")
	private String name;

    public UserRoleForm() {
    	
    }
    
    public UserRoleForm(UserRole userRole, boolean pendingSync, boolean syncFailed) {
    	this.id = userRole.getId();
    	this.name = userRole.getName();
    	this.identifier = userRole.getIdentifier();
    	this.userOnly = userRole.isUserOnly();
    	this.uuid = userRole.getUuid();
    	this.canRequest = userRole.isCanRequest();
    	this.description = userRole.getDescription();
    	this.itSystem = userRole.getItSystem();
    	this.sensitiveRole = userRole.isSensitiveRole();
    	this.extraSensitiveRole = userRole.isExtraSensitiveRole();
    	this.systemRoleAssignments = userRole.getSystemRoleAssignments();
    	this.delegatedFromCvr = userRole.getDelegatedFromCvr();
    	this.pendingSync = pendingSync;
    	this.syncFailed = syncFailed;
    	this.linkedSystemRole = userRole.getLinkedSystemRole();
    	this.allowPostponing = userRole.isAllowPostponing();
    	this.requireManagerAction = userRole.isRequireManagerAction();
    	this.sendToSubstitutes = userRole.isSendToSubstitutes();
    	this.sendToAuthorizationManagers = userRole.isSendToAuthorizationManagers();
		this.roleAssignmentAttestationByAttestationResponsible = userRole.isRoleAssignmentAttestationByAttestationResponsible();

    	if (userRole.getUserRoleEmailTemplate() != null) {
    		this.emailTemplateTitle = userRole.getUserRoleEmailTemplate().getTitle();
    		this.emailTemplateMessage = userRole.getUserRoleEmailTemplate().getMessage();
    	}
    }
    
    public UserRole toUserRole() {
    	UserRole userRole = new UserRole();
    	userRole.setId(this.id);
    	userRole.setName(this.name);
    	userRole.setIdentifier(this.identifier);
    	userRole.setUserOnly(this.userOnly);
    	userRole.setUuid(this.uuid);
    	userRole.setCanRequest(this.canRequest);
    	userRole.setDescription(this.description);
    	userRole.setItSystem(this.itSystem);
    	userRole.setSensitiveRole(this.sensitiveRole);
    	userRole.setExtraSensitiveRole(this.extraSensitiveRole);
    	userRole.setSystemRoleAssignments(this.systemRoleAssignments);
    	userRole.setDelegatedFromCvr(this.delegatedFromCvr);
    	userRole.setLinkedSystemRole(this.linkedSystemRole);
    	userRole.setAllowPostponing(this.allowPostponing);
    	userRole.setRequireManagerAction(this.requireManagerAction);
    	userRole.setSendToSubstitutes(this.sendToSubstitutes);
    	userRole.setSendToAuthorizationManagers(this.sendToAuthorizationManagers);
    	userRole.setRoleAssignmentAttestationByAttestationResponsible(this.roleAssignmentAttestationByAttestationResponsible);
    	if (this.isRequireManagerAction()) {
    		UserRoleEmailTemplate template = new UserRoleEmailTemplate();
    		template.setTitle(this.emailTemplateTitle);
    		template.setMessage(this.emailTemplateMessage);
    		template.setUserRole(userRole);
    		userRole.setUserRoleEmailTemplate(template);
    	}
    	
    	return userRole;
    }
}
