package dk.digitalidentity.rc.log;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ItSystemServiceAuditInterceptor {
	private final AuditLogger auditLogger;

	@Before(value = "execution(* dk.digitalidentity.rc.service.ItSystemService.*(..)) && @annotation(AuditLogIntercepted)")
	public void interceptBefore(JoinPoint jp) {
		if (jp.getSignature().getName().equals("save")) {
			auditSave(jp);
			return;
		}
		if (jp.getSignature().getName().equals("softDelete")) {
			auditDelete(jp);
			return;
		}
		log.error("Failed to intercept method: {}", jp.getSignature().getName());
	}

	private void auditSave(JoinPoint jp) {
		if (jp.getArgs().length > 0) {
			Object target = jp.getArgs()[0];

			if (target instanceof ItSystem itSystem) {
				boolean created = itSystem.getId() == 0;
				if (created) {
					auditLogger.log(itSystem, EventType.CREATE);
				} else {
					auditLogger.log(itSystem, EventType.UPDATE);
				}
			}
		}
	}

	private void auditDelete(JoinPoint jp) {
		if (jp.getArgs().length > 0) {
			Object target = jp.getArgs()[0];

			if (target instanceof ItSystem itSystem) {
				auditLogger.log(itSystem, EventType.DELETE);
			}
		}
	}
}
