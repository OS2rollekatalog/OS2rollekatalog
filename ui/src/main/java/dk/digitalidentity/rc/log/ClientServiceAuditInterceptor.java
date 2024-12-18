package dk.digitalidentity.rc.log;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class ClientServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@Before(value = "execution(* dk.digitalidentity.rc.service.ClientService.*(..)) && @annotation(AuditLogIntercepted)")
	public void interceptBefore(JoinPoint jp) {
		switch(jp.getSignature().getName()) {
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

	@Around(value = "execution(* dk.digitalidentity.rc.service.ClientService.*(..)) && @annotation(AuditLogIntercepted)")
	public Object interceptAround(ProceedingJoinPoint jp) throws Throwable {
		switch(jp.getSignature().getName()) {
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

			if (target != null && target instanceof Client) {
				auditLogger.log((Client) target, EventType.CLIENT_REMOVED);
			}
		}
	}

	private Object auditSave(ProceedingJoinPoint jp) throws Throwable {
		if (jp.getArgs().length > 0) {
			Object target = jp.getArgs()[0];

			if (target != null && target instanceof Client) {
				Client userRole = (Client) target;
				boolean created = false;

				if (userRole.getId() == 0) {
					created = true;
				}
				
				Client after = (Client) jp.proceed();
				if (created) {
					auditLogger.log(after, EventType.CLIENT_CREATED);
				} else {
				    auditLogger.log(after, EventType.CLIENT_CHANGED);
				}
				
				return after;
			}
		}
		
		return jp.proceed();
	}

}
