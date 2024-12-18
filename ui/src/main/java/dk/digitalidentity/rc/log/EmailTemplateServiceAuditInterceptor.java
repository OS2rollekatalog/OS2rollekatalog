package dk.digitalidentity.rc.log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.service.EmailTemplateService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class EmailTemplateServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private EmailTemplateService emailTemplateService;

    @Around(value = "execution(* dk.digitalidentity.rc.service.EmailTemplateService.*(..)) && @annotation(AuditLogIntercepted)")
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

    private Object auditSave(ProceedingJoinPoint jp) throws Throwable {
        if (jp.getArgs().length > 0) {
            Object target = jp.getArgs()[0];

            if (target != null && target instanceof EmailTemplate) {
                EmailTemplate emailTemplate = (EmailTemplate) target;
                boolean created = false;

                if (emailTemplate.getId() == 0) {
                    created = true;
                }
                
                EmailTemplate after = (EmailTemplate) jp.proceed();
                AuditLogContextHolder.getContext().addArgument("Navn", emailTemplateService.getTemplateName(after.getId()));
                auditLogger.log(after, EventType.EMAIL_TEMPLATE_CHANGED);
                AuditLogContextHolder.clearContext();
                
                return after;
            }
        }
        
        return jp.proceed();
    }

}
