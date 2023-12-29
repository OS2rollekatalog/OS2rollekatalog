package dk.digitalidentity.rc.log;

import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class OrgUnitServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@AfterReturning(value = "execution(* dk.digitalidentity.rc.service.OrgUnitService.*(..)) && @annotation(AuditLogIntercepted)")
	public void interceptAfter(JoinPoint jp) {
		try {
			switch(jp.getSignature().getName()) {
				case "addRoleGroup":
					auditAddRoleGroup(jp);
					break;
				case "removeRoleGroup":
					auditRemoveRoleGroup(jp);
					break;
				case "removeRoleGroupAssignment":
					auditRemoveRoleGroupAssignment(jp);
					break;
				case "updateRoleGroupAssignment":
					auditUpdateRoleGroupAssignment(jp);
					break;
				case "addUserRole":
					auditAddUserRole(jp);
					break;
				case "removeUserRole":
					auditRemoveUserRole(jp);
					break;
				case "removeUserRoleAssignment":
					auditRemoveUserRoleAssignment(jp);
					break;
				case "updateUserRoleAssignment":
					auditUpdateUserRoleAssignment(jp);
					break;
				case "addKLE":
					auditAddKle(jp);
					break;
				case "removeKLE":
					auditRemoveKle(jp);
					break;
				default:
					log.error("Failed to intercept method: " + jp.getSignature().getName());
					break;
			}
		} finally {
			AuditLogContextHolder.clearContext();
		}
	}

	private void auditAddKle(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length == 3 && args[0] instanceof OrgUnit && args[1] instanceof KleType && args[2] instanceof String)) {
			log.error("Method signature on addKLE does not match expectation");
			return;
		}

		KLEMapping kle = new KLEMapping();
		kle.setOrgUnit((OrgUnit) args[0]);
		kle.setCode((String) args[2]);
		kle.setAssignmentType((KleType) args[1]);

		auditLogger.log((OrgUnit) args[0], EventType.ASSIGN_KLE, kle);
	}

	private void auditRemoveKle(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length == 3 && args[0] instanceof OrgUnit && args[1] instanceof KleType && args[2] instanceof String)) {
			log.error("Method signature on removeKLE does not match expectation");
			return;
		}

		KLEMapping kle = new KLEMapping();
		kle.setOrgUnit((OrgUnit) args[0]);
		kle.setCode((String) args[2]);
		kle.setAssignmentType((KleType) args[1]);

		auditLogger.log((OrgUnit) args[0], EventType.REMOVE_KLE, kle);
	}

	private void auditAddUserRole(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!((args.length >= 2) && args[0] instanceof OrgUnit && args[1] instanceof UserRole)) {
			log.error("Method signature on addUserRole does not match expectation");
			return;
		}

		auditLogger.log((OrgUnit) args[0], EventType.ASSIGN_USER_ROLE, (UserRole) args[1]);
	}

	private void auditRemoveUserRole(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof OrgUnit && args[1] instanceof UserRole)) {
			log.error("Method signature on removeUserRole does not match expectation");
			return;
		}

		auditLogger.log((OrgUnit) args[0], EventType.REMOVE_USER_ROLE, (UserRole) args[1], AuditLogContextHolder.getContext().getStopDateUserId());
	}
	
	private void auditUpdateUserRoleAssignment(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof OrgUnit && args[1] instanceof OrgUnitUserRoleAssignment)) {
			log.error("Method signature on updateUserRoleAssignment does not match expectation");
			return;
		}

		UserRole userRole = ((OrgUnitUserRoleAssignment) args[1]).getUserRole();

		auditLogger.log((OrgUnit) args[0], EventType.EDIT_USER_ROLE_ASSIGNMENT, userRole);
	}
	
	private void auditRemoveUserRoleAssignment(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof OrgUnit && args[1] instanceof OrgUnitUserRoleAssignment)) {
			log.error("Method signature on removeUserRoleAssignment does not match expectation");
			return;
		}
		
		UserRole userRole = ((OrgUnitUserRoleAssignment) args[1]).getUserRole();

		auditLogger.log((OrgUnit) args[0], EventType.REMOVE_USER_ROLE, userRole, AuditLogContextHolder.getContext().getStopDateUserId());
	}

	private void auditAddRoleGroup(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!((args.length >= 2) && args[0] instanceof OrgUnit && args[1] instanceof RoleGroup)) {
			log.error("Method signature on addRoleGroup does not match expectation");
			return;
		}

		auditLogger.log((OrgUnit) args[0], EventType.ASSIGN_ROLE_GROUP, (RoleGroup) args[1]);
	}

	private void auditRemoveRoleGroup(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof OrgUnit && args[1] instanceof RoleGroup)) {
			log.error("Method signature on removeRoleGroup does not match expectation");
			return;
		}

		auditLogger.log((OrgUnit) args[0], EventType.REMOVE_ROLE_GROUP, (RoleGroup) args[1], AuditLogContextHolder.getContext().getStopDateUserId());
	}
	
	private void auditUpdateRoleGroupAssignment(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof OrgUnit && args[1] instanceof OrgUnitRoleGroupAssignment)) {
			log.error("Method signature on updateRoleGroupAssignment does not match expectation");
			return;
		}
		
		RoleGroup roleGroup = ((OrgUnitRoleGroupAssignment) args[1]).getRoleGroup();

		auditLogger.log((OrgUnit) args[0], EventType.EDIT_ROLE_GROUP_ASSIGNMENT, roleGroup);
	}
	
	private void auditRemoveRoleGroupAssignment(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof OrgUnit && args[1] instanceof OrgUnitRoleGroupAssignment)) {
			log.error("Method signature on removeRoleGroupAssignment does not match expectation");
			return;
		}
		
		RoleGroup roleGroup = ((OrgUnitRoleGroupAssignment) args[1]).getRoleGroup();

		auditLogger.log((OrgUnit) args[0], EventType.REMOVE_ROLE_GROUP, roleGroup, AuditLogContextHolder.getContext().getStopDateUserId());
	}
}
