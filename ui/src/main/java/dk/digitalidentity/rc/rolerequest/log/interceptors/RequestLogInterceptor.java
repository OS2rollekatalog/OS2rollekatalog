package dk.digitalidentity.rc.rolerequest.log.interceptors;

import dk.digitalidentity.rc.rolerequest.log.RequestAutditLogger;
import dk.digitalidentity.rc.rolerequest.log.RequestLoggable;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class RequestLogInterceptor {


	@Autowired
	private RequestService rolerequestService;

	@Autowired
	private RequestAutditLogger requestLogger;

	/**
	 * PointCut Signature that identifies all classes with the RequestLogInterceptable annotation
	 */
	@Pointcut("@annotation(requestLoggable)")
	public void requestLoggableMethods(RequestLoggable requestLoggable) {
	}

	/**
	 * PointCut Signature that identifies all classes with a Long parameter called requestId
	 */
	@Pointcut("args(requestId,..)")
	public void requestIdParameter(Long requestId) {
	}

	/**
	 * Pointcut matching any method that returns a RoleRequest object
	 */
	@Pointcut("execution(dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest *(..))")
	public void returnsRoleRequest() {
	}

	/**
	 * Interceptor for all methods with loggable annotation and a Long parameter calle requestId
	 * Gets the request details from the id
	 */
	@Around("requestLoggableMethods(requestLoggable) && requestIdParameter(requestId)")
	public void interceptParameterRequest(ProceedingJoinPoint joinPoint, RequestLoggable requestLoggable, Long requestId) throws Throwable {
		//Get relevant request
		RoleRequest request = rolerequestService.getRoleRequestById(requestId).orElse(null);
		if (request == null) {
			log.error("Did not log request. No request found with this id: {}", requestId);
			throw new IllegalArgumentException("Request not found, could not log request");
		}

		String details = switch (requestLoggable.logEvent()) {
			case REQUEST -> request.getReason();
			case DENY -> request.getRejectReason();
			case null, default -> "";
		};

		//execute the intercepted method
		Object returnValue = joinPoint.proceed();

		//Only logs if the intercepted method did not throw an exception
		requestLogger.logRequest(requestLoggable.logEvent(), request, details);
	}

	/**
	 * Interceptor for all methods with loggable annotation that returns a RoleRequest and does not have a parameter called requestId
	 * Gets the details from the RoleRequest
	 */
	@AfterReturning(pointcut = "requestLoggableMethods(requestLoggable) && returnsRoleRequest() && !requestIdParameter(Long)",
		returning = "request")
	public void interceptReturnRequest(RequestLoggable requestLoggable, RoleRequest request) {
		String details = switch (requestLoggable.logEvent()) {
			case REQUEST -> request.getReason();
			case DENY -> request.getRejectReason();
			case null, default -> "";
		};
		requestLogger.logRequest(requestLoggable.logEvent(), request, details);
	}

}
