package dk.digitalidentity.rc.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import lombok.extern.log4j.Log4j;

@Log4j
@Aspect
@Component
public class UserServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@AfterReturning(value = "execution(* dk.digitalidentity.rc.service.UserService.*(..)) && @annotation(AuditLogIntercepted)", returning = "retVal")
	public void interceptAfter(JoinPoint jp, Object retVal) {
		switch(jp.getSignature().getName()) {
			case "addRoleGroup":
				auditAddRoleGroup(jp, retVal);
				break;
			case "removeRoleGroup":
				auditRemoveRoleGroup(jp, retVal);
				break;
			case "addUserRole":
				auditAddUserRole(jp, retVal);
				break;
			case "removeUserRole":
				auditRemoveUserRole(jp, retVal);
				break;
			case "addKLE":
				auditAddKle(jp, retVal);
				break;
			case "removeKLE":
				auditRemoveKle(jp, retVal);
				break;
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				break;
		}
	}

	private void auditAddKle(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 3 && args[0] instanceof User && args[1] instanceof KleType && args[2] instanceof String)) {
			log.error("Method signature on addKLE does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			UserKLEMapping kle = new UserKLEMapping();
			kle.setUser((User) args[0]);
			kle.setCode((String) args[2]);
			kle.setAssignmentType((KleType) args[1]);

			auditLogger.log((User) args[0], EventType.ASSIGN_KLE, kle);
		}
	}

	private void auditRemoveKle(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 3 && args[0] instanceof User && args[1] instanceof KleType && args[2] instanceof String)) {
			log.error("Method signature on removeKLE does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			UserKLEMapping kle = new UserKLEMapping();
			kle.setUser((User) args[0]);
			kle.setCode((String) args[2]);
			kle.setAssignmentType((KleType) args[1]);

			auditLogger.log((User) args[0], EventType.REMOVE_KLE, kle);
		}
	}

	private void auditAddUserRole(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof User && args[1] instanceof UserRole)) {
			log.error("Method signature on addUserRole does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((User) args[0], EventType.ASSIGN_USER_ROLE, (UserRole) args[1]);
		}
	}

	private void auditRemoveUserRole(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof User && args[1] instanceof UserRole)) {
			log.error("Method signature on removeUserRole does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((User) args[0], EventType.REMOVE_USER_ROLE, (UserRole) args[1]);
		}
	}

	private void auditAddRoleGroup(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof User && args[1] instanceof RoleGroup)) {
			log.error("Method signature on addRoleGroup does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((User) args[0], EventType.ASSIGN_ROLE_GROUP, (RoleGroup) args[1]);
		}
	}

	private void auditRemoveRoleGroup(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof User && args[1] instanceof RoleGroup)) {
			log.error("Method signature on removeRoleGroup does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((User) args[0], EventType.REMOVE_ROLE_GROUP, (RoleGroup) args[1]);
		}
	}
}
