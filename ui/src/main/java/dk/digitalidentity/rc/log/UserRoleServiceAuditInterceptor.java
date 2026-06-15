package dk.digitalidentity.rc.log;

import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

	@Autowired
	private UserRoleDao userRoleDao;

	@PersistenceContext
	private EntityManager entityManager;

	@AfterReturning(value = "execution(* dk.digitalidentity.rc.service.UserRoleService.*(..)) && @annotation(AuditLogIntercepted)", returning = "retVal")
	public void interceptAfter(JoinPoint jp, Object retVal) {
		switch(jp.getSignature().getName()) {
			case "save":
			case "delete":
			case "updateSystemRoleConstraint":
			case "addSystemRoleConstraint":
			case "removeSystemRoleConstraint":
			case "replaceSystemRoleConstraints":
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
			case "replaceSystemRoleConstraints":
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
			case "replaceSystemRoleConstraints":
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
				boolean created = userRole.getId() == 0;

				if (!created) {
					// open-in-view keeps the JPA session alive for the whole HTTP request, so the entity
					// passed to save() is already mutated in-memory. Detach it, load the persisted state
					// for snapshotting, then re-merge so jp.proceed() can still save the new values.
					entityManager.detach(userRole);
					userRoleDao.findById(userRole.getId()).ifPresent(this::snapshotBeforeState);
					entityManager.merge(userRole);
				}

		    	UserRole after = (UserRole) jp.proceed();
		        if (created) {
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

	private Object auditSystemRoleAssignmentUpdate(final ProceedingJoinPoint jp, final EventType eventType) throws Throwable {
		Object[] args = jp.getArgs();
		if (args.length >= 1 && args[0] instanceof SystemRoleAssignment) {
			auditLogger.log(((SystemRoleAssignment) args[0]).getUserRole(), eventType, ((SystemRoleAssignment) args[0]).getSystemRole());
		} else {
			log.error("Method signature does not match expectation");
		}
		return jp.proceed();
	}

	private void snapshotBeforeState(UserRole role) {
		AuditLogContext ctx = AuditLogContextHolder.getContext();
		ctx.putBefore(AuditLogContext.FIELD_NAVN, role.getName());
		ctx.putBefore(AuditLogContext.FIELD_BESKRIVELSE, role.getDescription());
		ctx.putBefore(AuditLogContext.FIELD_KUN_BRUGERE, String.valueOf(role.isUserOnly()));
		ctx.putBefore(AuditLogContext.FIELD_FOELSOM_ROLLE, String.valueOf(role.isSensitiveRole()));
		ctx.putBefore(AuditLogContext.FIELD_EKSTRA_FOELSOM_ROLLE, String.valueOf(role.isExtraSensitiveRole()));
		ctx.putBefore(AuditLogContext.FIELD_ATTESTATION_AF_ATTESTATIONSANSVARLIG, String.valueOf(role.isRoleAssignmentAttestationByAttestationResponsible()));
		ctx.putBefore(AuditLogContext.FIELD_KRAEVER_LEDERHANDLING, String.valueOf(role.isRequireManagerAction()));
		ctx.putBefore(AuditLogContext.FIELD_SEND_TIL_BEMYNDIGELSESANSVARLIGE, String.valueOf(role.isSendToAuthorizationManagers()));
		ctx.putBefore(AuditLogContext.FIELD_SEND_TIL_STEDFORTRAEDERE, String.valueOf(role.isSendToSubstitutes()));
		ctx.putBefore(AuditLogContext.FIELD_KAN_ANMODE, role.getRequesterPermission());
		ctx.putBefore(AuditLogContext.FIELD_KAN_GODKENDE, role.getApproverPermission());
		ctx.putBefore(AuditLogContext.FIELD_ENHEDSFILTER, String.valueOf(role.isOuFilterEnabled()));
	}

	private void buildUpdateDescription(UserRole after) {
		AuditLogContext ctx = AuditLogContextHolder.getContext();
		ctx.diff(AuditLogContext.FIELD_NAVN, after.getName());
		ctx.diff(AuditLogContext.FIELD_BESKRIVELSE, after.getDescription());
		ctx.diff(AuditLogContext.FIELD_KUN_BRUGERE, after.isUserOnly());
		ctx.diff(AuditLogContext.FIELD_FOELSOM_ROLLE, after.isSensitiveRole());
		ctx.diff(AuditLogContext.FIELD_EKSTRA_FOELSOM_ROLLE, after.isExtraSensitiveRole());
		ctx.diff(AuditLogContext.FIELD_ATTESTATION_AF_ATTESTATIONSANSVARLIG, after.isRoleAssignmentAttestationByAttestationResponsible());
		ctx.diff(AuditLogContext.FIELD_KRAEVER_LEDERHANDLING, after.isRequireManagerAction());
		ctx.diff(AuditLogContext.FIELD_SEND_TIL_BEMYNDIGELSESANSVARLIGE, after.isSendToAuthorizationManagers());
		ctx.diff(AuditLogContext.FIELD_SEND_TIL_STEDFORTRAEDERE, after.isSendToSubstitutes());
		ctx.diff(AuditLogContext.FIELD_KAN_ANMODE, after.getRequesterPermission());
		ctx.diff(AuditLogContext.FIELD_KAN_GODKENDE, after.getApproverPermission());
		ctx.diff(AuditLogContext.FIELD_ENHEDSFILTER, after.isOuFilterEnabled());
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
