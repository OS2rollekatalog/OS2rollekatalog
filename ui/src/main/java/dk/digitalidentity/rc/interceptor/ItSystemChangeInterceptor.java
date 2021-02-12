package dk.digitalidentity.rc.interceptor;

import java.util.Objects;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
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
				boolean created = false, changes = false;

				if (systemRole.getId() == 0) {
					created = true;
				}

				ItSystemChange newItSystemChange = new ItSystemChange();
				newItSystemChange.setItSystemId(systemRole.getItSystem().getId());
				newItSystemChange.setSystemRoleId(systemRole.getId());

				// preload before we detach
				if (systemRole.getSupportedConstraintTypes() != null) {
					systemRole.getSupportedConstraintTypes().size();
				}
				
				entityManager.detach(systemRole);

				SystemRole oldSysRole = systemRoleService.getById(systemRole.getId());
				if (oldSysRole != null) {
					String prevName = oldSysRole.getName();
					String prevDescription = oldSysRole.getDescription();
					
					long oldSysRoleConstraintCount = oldSysRole.getSupportedConstraintTypes() != null ? oldSysRole.getSupportedConstraintTypes().size() : 0;
					long systemRoleConstraintCount = systemRole.getSupportedConstraintTypes() != null ? systemRole.getSupportedConstraintTypes().size() : 0;
					boolean constraintsChanged = false;
					if (oldSysRoleConstraintCount != systemRoleConstraintCount) {
						constraintsChanged = true;
					}
					else if (oldSysRoleConstraintCount > 0) {
						// same count, but still > 0, check for changes

						for (ConstraintTypeSupport oldConstraint : oldSysRole.getSupportedConstraintTypes()) {
							boolean found = false;
							
							for (ConstraintTypeSupport newConstraint : systemRole.getSupportedConstraintTypes()) {
								if (oldConstraint.getConstraintType().getEntityId().contentEquals(newConstraint.getConstraintType().getEntityId())) {
									found = true;
									
									if (newConstraint.isMandatory() != oldConstraint.isMandatory()) {
										constraintsChanged = true;
									}
								}
							}
							
							if (!found) {
								constraintsChanged = true;
							}
						}
					}

					// figure out if there are actual changes
					if (constraintsChanged) {
						changes = true;
					}
					else if (!Objects.equals(prevName, systemRole.getName())) {
						changes = true;
					}
					
					// special extra check to avoid ("" != null) causing email notifications 
					if (StringUtils.isEmpty(prevDescription) && StringUtils.isEmpty(systemRole.getDescription())) {
						; // don't make any changes to "changes" status
					}
					else if (!Objects.equals(prevDescription, systemRole.getDescription())) {
						changes = true;
					}

					newItSystemChange.setSystemRoleName(prevName);
					newItSystemChange.setSystemRoleIdentifier(systemRole.getIdentifier());
					newItSystemChange.setSystemRoleDescription(prevDescription);
					newItSystemChange.setSystemRoleConstraintChanged(constraintsChanged);
				}
				else {
					newItSystemChange.setSystemRoleName(systemRole.getName());
					newItSystemChange.setSystemRoleIdentifier(systemRole.getIdentifier());
					newItSystemChange.setSystemRoleDescription(systemRole.getDescription());
				}

				if (created || changes) {
					newItSystemChange.setEventType((created) ? ItSystemChangeEventType.SYSTEM_ROLE_ADD : ItSystemChangeEventType.SYSTEM_ROLE_MODIFY);
					itSystemChangeService.save(newItSystemChange);
				}
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
