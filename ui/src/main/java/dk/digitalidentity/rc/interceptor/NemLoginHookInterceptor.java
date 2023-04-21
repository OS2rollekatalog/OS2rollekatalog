package dk.digitalidentity.rc.interceptor;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.NemLoginGroupEventType;
import dk.digitalidentity.rc.service.PendingNemLoginGroupUpdateService;

@Aspect
public class NemLoginHookInterceptor {

	@Autowired
	private PendingNemLoginGroupUpdateService pendingNemLoginGroupUpdateService;

	@After("execution(* dk.digitalidentity.rc.service.UserRoleService.save(dk.digitalidentity.rc.dao.model.UserRole)) && args(userRole)")
	public void interceptSaveUserRole(UserRole userRole) {
		if (userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			pendingNemLoginGroupUpdateService.addUserRoleToQueue(userRole, NemLoginGroupEventType.UPDATE);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserRoleService.delete(dk.digitalidentity.rc.dao.model.UserRole)) && args(userRole)")
	public void interceptDeleteUserRole(UserRole userRole) {
		if (userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN) && userRole.getUuid() != null) {
			pendingNemLoginGroupUpdateService.addUserRoleToQueue(userRole, NemLoginGroupEventType.DELETE);
		}
	}
}
