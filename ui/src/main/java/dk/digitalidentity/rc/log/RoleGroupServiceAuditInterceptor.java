package dk.digitalidentity.rc.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.extern.log4j.Log4j;

@Log4j
@Aspect
@Component
public class RoleGroupServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@AfterReturning(value = "execution(* dk.digitalidentity.rc.service.RoleGroupService.*(..)) && @annotation(AuditLogIntercepted)", returning = "retVal")
	public void interceptAfter(JoinPoint jp, Object retVal) {
		switch(jp.getSignature().getName()) {
			case "addUserRole":
				auditAddUserRole(jp, retVal);
				break;
			case "removeUserRole":
				auditRemoveUserRole(jp, retVal);
				break;
			case "save":
			case "delete":
				break;
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				break;
		}
	}
	
	@Before(value = "execution(* dk.digitalidentity.rc.service.RoleGroupService.*(..)) && @annotation(AuditLogIntercepted)")
	public void interceptBefore(JoinPoint jp) {
		switch(jp.getSignature().getName()) {
			case "addUserRole":
			case "removeUserRole":
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

	@Around(value = "execution(* dk.digitalidentity.rc.service.RoleGroupService.*(..)) && @annotation(AuditLogIntercepted)")
	public Object interceptAround(ProceedingJoinPoint jp) throws Throwable {
		switch(jp.getSignature().getName()) {
			case "save":
				return auditSave(jp);
			case "addUserRole":
			case "removeUserRole":
			case "delete":
				return jp.proceed();
			default:
				log.error("Failed to intercept method: " + jp.getSignature().getName());
				return jp.proceed();
		}
	}

	private void auditDelete(JoinPoint jp) {
        if (jp.getArgs().length > 0) {
            Object target = jp.getArgs()[0];

            if (target != null && target instanceof RoleGroup) {
            	auditLogger.log((RoleGroup) target, EventType.DELETE);
            }
        }
	}

	private Object auditSave(ProceedingJoinPoint jp) throws Throwable {
        if (jp.getArgs().length > 0) {
            Object target = jp.getArgs()[0];

            if (target != null && target instanceof RoleGroup) {
            	RoleGroup roleGroup = (RoleGroup) target;
				boolean created = false;

		    	if (roleGroup.getId() == 0) {
		    		created = true;
		        }
		    	
		        RoleGroup after = (RoleGroup) jp.proceed();
		        if (created) {
		        	auditLogger.log(after, EventType.CREATE);
		        }
		        
		        return after;
            }
        }
        
        return jp.proceed();
	}

	private void auditAddUserRole(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof RoleGroup && args[1] instanceof UserRole)) {
			log.error("Method signature on addUserRole does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((RoleGroup) args[0], EventType.ASSIGN_USER_ROLE, (UserRole) args[1]);
		}
	}

	private void auditRemoveUserRole(JoinPoint jp, Object retVal) {
		Object[] args = jp.getArgs();
		if (!(args.length == 2 && args[0] instanceof RoleGroup && args[1] instanceof UserRole)) {
			log.error("Method signature on removeUserRole does not match expectation");
			return;
		}

		if (retVal instanceof Boolean && ((boolean) retVal) == true) {
			auditLogger.log((RoleGroup) args[0], EventType.REMOVE_USER_ROLE, (UserRole) args[1]);
		}
	}
}
