package dk.digitalidentity.rc.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.extern.log4j.Log4j;

@Log4j
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
