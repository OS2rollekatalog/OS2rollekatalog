package dk.digitalidentity.rc.log;

import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class UserRoleServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@AfterReturning(value = "execution(* dk.digitalidentity.rc.service.UserRoleService.*(..)) && @annotation(AuditLogIntercepted)", returning = "retVal")
	public void interceptAfter(JoinPoint jp, Object retVal) {
		switch(jp.getSignature().getName()) {
			case "save":
			case "delete":
			case "updateSystemRoleConstraint":
			case "addSystemRoleConstraint":
			case "removeSystemRoleConstraint":
				break;
			case "addSystemRoleAssignment":
				auditAddSystemRoleAssignment(jp, retVal);
				break;
			case "removeSystemRoleAssignment":
				auditRemoveSystemRoleAssignment(jp, retVal);
				break;
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				break;
		}
	}
	
	@Before(value = "execution(* dk.digitalidentity.rc.service.UserRoleService.*(..)) && @annotation(AuditLogIntercepted)")
	public void interceptBefore(JoinPoint jp) {
		switch(jp.getSignature().getName()) {
			case "addSystemRoleAssignment":
			case "removeSystemRoleAssignment":
			case "save":
			case "updateSystemRoleConstraint":
			case "addSystemRoleConstraint":
			case "removeSystemRoleConstraint":
				break;
			case "delete":
				auditDelete(jp);
				break;
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				break;
		}
	}

	@Around(value = "execution(* dk.digitalidentity.rc.service.UserRoleService.*(..)) && @annotation(AuditLogIntercepted)")
	public Object interceptAround(ProceedingJoinPoint jp) throws Throwable {
		switch(jp.getSignature().getName()) {
			case "addSystemRoleAssignment":
			case "removeSystemRoleAssignment":
			case "delete":
				return jp.proceed();
			case "save":
				return auditSave(jp);
			case "addSystemRoleConstraint":
				return auditSystemRoleAssignmentUpdate(jp, EventType.ADD_ASSIGNMENT_CONSTRAINT);
			case "updateSystemRoleConstraint":
				return auditSystemRoleAssignmentUpdate(jp, EventType.EDIT_ASSIGNMENT_CONSTRAINT);
			case "removeSystemRoleConstraint":
				return auditSystemRoleAssignmentUpdate(jp, EventType.REMOVE_SYSTEM_ROLE_CONSTRAINT);
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				return jp.proceed();
		}
	}

	private void auditDelete(JoinPoint jp) {
        if (jp.getArgs().length > 0) {
            Object target = jp.getArgs()[0];

            if (target != null && target instanceof UserRole) {
            	auditLogger.log((UserRole) target, EventType.DELETE);
            }
        }
	}

	private Object auditSave(ProceedingJoinPoint jp) throws Throwable {
        if (jp.getArgs().length > 0) {
            Object target = jp.getArgs()[0];

            if (target != null && target instanceof UserRole) {
            	UserRole userRole = (UserRole) target;
				boolean created = false;

		    	if (userRole.getId() == 0) {
		    		created = true;
		        }
		    	
		    	UserRole after = (UserRole) jp.proceed();
		        if (created) {
		        	auditLogger.log(after, EventType.CREATE);
		        }
		        
		        return after;
            }
        }
        
        return jp.proceed();
	}

	private Object auditSystemRoleAssignmentUpdate(final ProceedingJoinPoint jp, final EventType eventType) throws Throwable {
		Object[] args = jp.getArgs();
		if (args.length >= 1 && args[0] instanceof SystemRoleAssignment) {
			auditLogger.log(((SystemRoleAssignment) args[0]).getUserRole(), eventType, ((SystemRoleAssignment) args[0]).getSystemRole());
		} else {
			log.error("Method signature does not match expectation");
		}
		return jp.proceed();
	}

	private void auditAddSystemRoleAssignment(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof UserRole && args[1] instanceof SystemRoleAssignment)) {
			log.error("Method signature on addSystemRoleAssignment does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((UserRole) args[0], EventType.ASSIGN_SYSTEMROLE, ((SystemRoleAssignment) args[1]).getSystemRole());
		}
	}

	private void auditRemoveSystemRoleAssignment(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof UserRole && args[1] instanceof SystemRoleAssignment)) {
			log.error("Method signature on removeSystemRoleAssignment does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((UserRole) args[0], EventType.REMOVE_SYSTEMROLE, ((SystemRoleAssignment) args[1]).getSystemRole());
		}
	}
}
