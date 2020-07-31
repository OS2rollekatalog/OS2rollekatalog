package dk.digitalidentity.rc.interceptor;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemChange;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemChangeEventType;
import dk.digitalidentity.rc.service.ItSystemChangeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import lombok.extern.log4j.Log4j;

@Aspect
@Log4j
@Component
public class ItSystemChangeInterceptor {

	@PersistenceContext
	private EntityManager entityManager;
	
	@Autowired
	private ItSystemChangeService itSystemChangeService;
	
	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Around("execution(* dk.digitalidentity.rc.service.SystemRoleService.save(..))")
	public Object interceptSystemRoleSave(ProceedingJoinPoint joinPoint) throws Throwable {
		if (joinPoint.getArgs().length > 0) {
			Object target = joinPoint.getArgs()[0];

			if (target != null && target instanceof SystemRole) {
				SystemRole systemRole = (SystemRole) target;
				boolean created = false;
				
				if (systemRole.getId() == 0) {
					created = true;
				}

				ItSystemChange newItSystemChange = new ItSystemChange();
				newItSystemChange.setItSystemId(systemRole.getItSystem().getId());
				newItSystemChange.setSystemRoleId(systemRole.getId());

				entityManager.detach(systemRole);

				SystemRole oldSysRole = systemRoleService.getById(systemRole.getId());
				if (oldSysRole != null) {
					String prevName = oldSysRole.getName();
					String prevDescription = oldSysRole.getDescription();
					boolean constraintsChanged = oldSysRole.getSupportedConstraintTypes().equals(systemRole.getSupportedConstraintTypes());
	
					newItSystemChange.setSystemRoleName(prevName);
					newItSystemChange.setSystemRoleIdentifier(systemRole.getIdentifier());
					newItSystemChange.setSystemRoleDescription(prevDescription);
					newItSystemChange.setSystemRoleConstraintChanged(constraintsChanged);
				}
				else {
					newItSystemChange.setSystemRoleName(systemRole.getName());
					newItSystemChange.setSystemRoleIdentifier(systemRole.getIdentifier());
				}

				if (created) {
					newItSystemChange.setEventType(ItSystemChangeEventType.SYSTEM_ROLE_ADD);
				}
				else {
					newItSystemChange.setEventType(ItSystemChangeEventType.SYSTEM_ROLE_MODIFY);
				}
				SystemRole after = (SystemRole) joinPoint.proceed();

				itSystemChangeService.save(newItSystemChange);

				return after;
			}
			else {
				log.info("Not a type of SystemRole. Actually: " + (target != null ? target.getClass().getSimpleName() : null));
			}
		}

		// default
		return joinPoint.proceed();
	}

	@Before("execution(* dk.digitalidentity.rc.service.SystemRoleService.delete(dk.digitalidentity.rc.dao.model.SystemRole)) && args(systemRole)")
	public void interceptSystemRoleDelete(SystemRole systemRole) {
		ItSystemChange newItSystemChange = new ItSystemChange();
		newItSystemChange.setItSystemId(systemRole.getItSystem().getId());
		newItSystemChange.setSystemRoleId(systemRole.getId());
		newItSystemChange.setEventType(ItSystemChangeEventType.SYSTEM_ROLE_REMOVE);
		newItSystemChange.setSystemRoleName(systemRole.getName());
		newItSystemChange.setSystemRoleIdentifier(systemRole.getIdentifier());

		itSystemChangeService.save(newItSystemChange);
	}

	@Around("execution(* dk.digitalidentity.rc.service.ItSystemService.save(..))")
	public Object interceptItSystemSave(ProceedingJoinPoint joinPoint) throws Throwable {
		if (joinPoint.getArgs().length > 0) {
			Object target = joinPoint.getArgs()[0];

			if (target != null && target instanceof ItSystem) {
				ItSystem itSystem = (ItSystem) target;
				boolean created = (itSystem.getId() == 0);

				ItSystemChange itSystemChange = new ItSystemChange();
				itSystemChange.setEventType(created ? ItSystemChangeEventType.ITSYSTEM_NEW : ItSystemChangeEventType.ITSYSTEM_MODIFY);

				if (!created) {
					entityManager.detach(itSystem);
	
					String prevName = null;
					ItSystem oldItSystem = itSystemService.getById(itSystem.getId());
					if (oldItSystem != null) {
						prevName = oldItSystem.getName();
					}
					itSystemChange.setItSystemName(prevName);
				}

				ItSystem after = (ItSystem) joinPoint.proceed();
				itSystemChange.setItSystemId(after.getId());

				itSystemChangeService.save(itSystemChange);

				return after;
			}
			else {
				log.info("Not a type of ItSystem. Actually: " + (target != null ? target.getClass().getSimpleName() : null));
			}
		}

		// default
		return joinPoint.proceed();
	}
}
