package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Aspect
@Component
public class PermissionAspect {
	private final UserPermissionContext userPermissionContext;

	// Handle method-level annotations
	@Around("@annotation(requirePermission)")
	public Object checkMethodPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
		if (!userPermissionContext.hasPermission(requirePermission.section(), requirePermission.permission())) {
			throw new NotPermittedException(
					"Missing access permission for: " + joinPoint.getSignature().getDeclaringTypeName() + ":" + joinPoint.getSignature().getName(),
					requirePermission.section(),
					requirePermission.permission());
		}
		return joinPoint.proceed();
	}

	// Handle class-level annotations
	@Around("@within(requirePermission)")
	public Object checkClassPermission(ProceedingJoinPoint joinPoint, RequireControllerPermission requirePermission) throws Throwable {
		if (!userPermissionContext.hasPermission(requirePermission.section(), requirePermission.permission())) {
			throw new NotPermittedException(
					"Missing access permission for: " + joinPoint.getSignature().getDeclaringTypeName() + ":" + joinPoint.getSignature().getName(),
					requirePermission.section(),
					requirePermission.permission());
		}
		return joinPoint.proceed();
	}
}