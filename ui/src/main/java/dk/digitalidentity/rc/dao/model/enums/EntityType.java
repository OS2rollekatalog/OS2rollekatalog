package dk.digitalidentity.rc.dao.model.enums;

import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.FrontPageLink;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Getter;

@Getter
public enum EntityType {
	ORGUNIT("enum.entitytype.orgunit"),
	POSITION("enum.entitytype.position"),
	ROLEGROUP("enum.entitytype.rolegroup"),
	USER("enum.entitytype.user"),
	TITLE("enum.entitytype.title"),
	USERROLE("enum.entitytype.userrole"),
	ITSYSTEM("enum.entitytype.itsystem"),
	SYSTEMROLE("enum.entitytype.systemrole"),
	KLE_PERFORMING("enum.entitytype.kleperform"),
	KLE_INTEREST("enum.entitytype.kleinterest"),
	REQUEST_APPROVE("enum.entitytype.requestapprove"),
	SETTING("enum.entitytype.setting"),
	FRONT_PAGE_LINK("enum.entitytype.frontpagelink"),
	EMAIL_TEMPLATE("enum.entitytype.emailtemplate"),
	CLIENT("enum.entitytype.client");
	
	private EntityType(String message) {
		this.message = message;
	}
	
	private String message;

	public static EntityType getEntityType(AuditLoggable object) {
		if (object instanceof OrgUnit) {
			return ORGUNIT;
		}
		else if (object instanceof Position) {
			return POSITION;
		}
		else if (object instanceof RoleGroup) {
			return ROLEGROUP;
		}
		else if (object instanceof User) {
			return USER;
		}
		else if (object instanceof Title) {
			return TITLE;
		}
		else if (object instanceof UserRole) {
			return USERROLE;
		}
		else if (object instanceof ItSystem) {
			return ITSYSTEM;
		}
		else if (object instanceof SystemRole) {
			return SYSTEMROLE;
		}
		else if (object instanceof RequestApprove) {
			return REQUEST_APPROVE;
		}
		else if (object instanceof KLEMapping) {
			KLEMapping mapping = (KLEMapping) object;

			if (mapping.getAssignmentType().equals(KleType.PERFORMING)) {
				return KLE_PERFORMING;
			}
			else if (mapping.getAssignmentType().equals(KleType.INTEREST)) {
				return KLE_INTEREST;
			}
			else {
				throw new IllegalArgumentException("Unknown KLEMapping type: " + mapping.getAssignmentType());
			}
		}
		else if (object instanceof UserKLEMapping) {
			UserKLEMapping mapping = (UserKLEMapping) object;

			if (mapping.getAssignmentType().equals(KleType.PERFORMING)) {
				return KLE_PERFORMING;
			}
			else if (mapping.getAssignmentType().equals(KleType.INTEREST)) {
				return KLE_INTEREST;
			}
			else {
				throw new IllegalArgumentException("Unknown KLEMapping type: " + mapping.getAssignmentType());
			}
		}
		if (object instanceof Setting) {
			return SETTING;
		}
		if (object instanceof FrontPageLink) {
			return FRONT_PAGE_LINK;
		}
		if (object instanceof EmailTemplate) {
			return EMAIL_TEMPLATE;
		}
		if (object instanceof Client) {
			return CLIENT;
		}
		else {
			throw new IllegalArgumentException("Unknown object type: " + object.getClass().getName());
		}
	}
}
