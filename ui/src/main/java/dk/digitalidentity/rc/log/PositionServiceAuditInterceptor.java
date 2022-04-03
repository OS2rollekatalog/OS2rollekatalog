package dk.digitalidentity.rc.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class PositionServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@AfterReturning(value = "execution(* dk.digitalidentity.rc.service.PositionService.*(..)) && @annotation(AuditLogIntercepted)")
	public void interceptAfter(JoinPoint jp) {
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
			case "addUserRole":
				auditAddUserRole(jp);
				break;
			case "removeUserRole":
				auditRemoveUserRole(jp);
				break;
			case "removeUserRoleAssignment":
				auditRemoveUserRoleAssignment(jp);
				break;
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				break;
		}
	}

	private void auditAddUserRole(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof Position && args[1] instanceof UserRole)) {
			log.error("Method signature on addUserRole does not match expectation");
			return;
		}

		auditLogger.log((Position) args[0], EventType.ASSIGN_USER_ROLE, (UserRole) args[1]);
	}

	private void auditRemoveUserRole(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof Position && args[1] instanceof UserRole)) {
			log.error("Method signature on removeUserRole does not match expectation");
			return;
		}

		auditLogger.log((Position) args[0], EventType.REMOVE_USER_ROLE, (UserRole) args[1]);
	}
	
	private void auditRemoveUserRoleAssignment(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof Position && args[1] instanceof PositionUserRoleAssignment)) {
			log.error("Method signature on removeUserRole does not match expectation");
			return;
		}
		
		UserRole userRole = ((PositionUserRoleAssignment) args[1]).getUserRole();

		auditLogger.log((Position) args[0], EventType.REMOVE_USER_ROLE, userRole);
	}

	private void auditAddRoleGroup(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof Position && args[1] instanceof RoleGroup)) {
			log.error("Method signature on addRoleGroup does not match expectation");
			return;
		}

		auditLogger.log((Position) args[0], EventType.ASSIGN_ROLE_GROUP, (RoleGroup) args[1]);
	}

	private void auditRemoveRoleGroup(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof Position && args[1] instanceof RoleGroup)) {
			log.error("Method signature on removeRoleGroup does not match expectation");
			return;
		}

		auditLogger.log((Position) args[0], EventType.REMOVE_ROLE_GROUP, (RoleGroup) args[1]);
	}
	
	private void auditRemoveRoleGroupAssignment(JoinPoint jp) {
		Object[] args = jp.getArgs();
		if (!(args.length >= 2 && args[0] instanceof Position && args[1] instanceof PositionRoleGroupAssignment)) {
			log.error("Method signature on removeRoleGroup does not match expectation");
			return;
		}

		RoleGroup roleGroup = ((PositionRoleGroupAssignment) args[1]).getRoleGroup();
		
		auditLogger.log((Position) args[0], EventType.REMOVE_ROLE_GROUP, roleGroup);
	}
}
