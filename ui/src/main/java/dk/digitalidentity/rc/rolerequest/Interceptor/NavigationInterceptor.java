package dk.digitalidentity.rc.rolerequest.Interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.rolerequest.service.permission.ApprovableRequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NavigationInterceptor implements HandlerInterceptor {
	private final RequestService rolerequestService;
	private final UserService userService;
	private final ApprovableRequestService approvableRequestService;

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception)
		throws Exception {

	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView)
		throws Exception {

	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());

		boolean isAdmin = SecurityUtil.hasDirectAdminRole();
		boolean isAuthorizationResponsibleAnywhere = rolerequestService.isAuthorizationResponsibleAnywhere(loggedInUser);
		boolean isRequestAuthorizedAnywhere = rolerequestService.isRequestAuthorizedAnywhere();
		boolean isApproveAuthorizedAnywhere = rolerequestService.isApproveAuthorizedAnywhere();
		boolean isSystemResponsibleAnywhere = rolerequestService.isSystemResponsibleAnywhere(loggedInUser);
		boolean isManualEffectuationSystemOwnerAnywhere = SecurityUtil.hasDirectAdminRole() || SecurityUtil.hasRole(Constants.ROLE_MANUAL_EFFECTUATION_SYSTEM_OWNER);

		request.setAttribute("isAdmin", isAdmin);
		request.setAttribute("isManagerOrSubstituteAnywhere", rolerequestService.isManagerAnywhere(loggedInUser));
		request.setAttribute("isSystemOwnerAnywhere", isSystemResponsibleAnywhere);
		request.setAttribute("isRequestAuthorizedAnywhere", isRequestAuthorizedAnywhere);
		request.setAttribute("isApproveAuthorizedAnywhere", isApproveAuthorizedAnywhere);
		request.setAttribute("isAuthorizationResponsibleAnywhere", isAuthorizationResponsibleAnywhere);
		request.setAttribute("isManualEffectuationSystemOwnerAnywhere", isManualEffectuationSystemOwnerAnywhere);

		// optimize order by checking the pre-computed fields first, and THEN doing sql lookups if needed
		if (isAdmin ||
			isAuthorizationResponsibleAnywhere ||
			isRequestAuthorizedAnywhere ||
			isApproveAuthorizedAnywhere ||
			isSystemResponsibleAnywhere ||
			rolerequestService.isManagerAnywhere(loggedInUser)) {

			request.setAttribute("waitingRequestsCount", approvableRequestService.countPendingApprovable(loggedInUser));
		}
		else {
			request.setAttribute("waitingRequestsCount", 0);
		}

		return true;
	}
}
