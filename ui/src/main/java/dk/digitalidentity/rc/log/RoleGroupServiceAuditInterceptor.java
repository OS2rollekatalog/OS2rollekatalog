package dk.digitalidentity.rc.log;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class RoleGroupServiceAuditInterceptor {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private RoleGroupDao roleGroupDao;

	@PersistenceContext
	private EntityManager entityManager;

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
		if (jp.getArgs().length > 0 && jp.getArgs()[0] instanceof RoleGroup roleGroup) {
			auditLogger.log(roleGroup, EventType.DELETE);
		}
	}

	private Object auditSave(ProceedingJoinPoint jp) throws Throwable {
		if (jp.getArgs().length > 0) {
			Object target = jp.getArgs()[0];

			if (target != null && target instanceof RoleGroup roleGroup) {
				boolean isCreate = roleGroup.getId() == 0;

				if (!isCreate) {
					entityManager.detach(roleGroup);
					roleGroupDao.findById(roleGroup.getId()).ifPresent(this::snapshotBeforeState);
					entityManager.merge(roleGroup);
				}

				RoleGroup after = (RoleGroup) jp.proceed();

				if (isCreate) {
					auditLogger.log(after, EventType.CREATE);
				} else {
					buildUpdateDescription(after);
					if (AuditLogContextHolder.getContext().getArguments() != null) {
						auditLogger.log(after, EventType.UPDATE);
					}
				}

				AuditLogContextHolder.clearContext();
				return after;
			}
		}

		return jp.proceed();
	}

	private void snapshotBeforeState(RoleGroup role) {
		AuditLogContext ctx = AuditLogContextHolder.getContext();
		ctx.putBefore(AuditLogContext.FIELD_NAVN, role.getName());
		ctx.putBefore(AuditLogContext.FIELD_BESKRIVELSE, role.getDescription());
		ctx.putBefore(AuditLogContext.FIELD_KUN_BRUGERE, String.valueOf(role.isUserOnly()));
		ctx.putBefore(AuditLogContext.FIELD_KAN_ANMODE, role.getRequesterPermission());
		ctx.putBefore(AuditLogContext.FIELD_KAN_GODKENDE, role.getApproverPermission());
		ctx.putBefore(AuditLogContext.FIELD_ENHEDSFILTER, String.valueOf(role.isOuFilterEnabled()));
	}

	private void buildUpdateDescription(RoleGroup after) {
		AuditLogContext ctx = AuditLogContextHolder.getContext();
		ctx.diff(AuditLogContext.FIELD_NAVN, after.getName());
		ctx.diff(AuditLogContext.FIELD_BESKRIVELSE, after.getDescription());
		ctx.diff(AuditLogContext.FIELD_KUN_BRUGERE, after.isUserOnly());
		ctx.diff(AuditLogContext.FIELD_KAN_ANMODE, after.getRequesterPermission());
		ctx.diff(AuditLogContext.FIELD_KAN_GODKENDE, after.getApproverPermission());
		ctx.diff(AuditLogContext.FIELD_ENHEDSFILTER, after.isOuFilterEnabled());
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
