package dk.digitalidentity.rc.rolerequest.Interceptor;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.rolerequest.service.WaitingRequestsService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@RequiredArgsConstructor
public class NavigationInterceptor implements HandlerInterceptor {
	private final RequestService rolerequestService;
	private final UserService userService;
	private final WaitingRequestsService waitingRequestsService;

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

		request.setAttribute("isAdmin", SecurityUtil.isAdmin());
		request.setAttribute("isManagerOrSubstituteAnywhere", rolerequestService.isManagerAnywhere(loggedInUser));
		request.setAttribute("isSystemOwnerAnywhere", rolerequestService.isSystemResponsibleAnywhere(loggedInUser));
		request.setAttribute("isRequestAuthorizedAnywhere", rolerequestService.isRequestAuthorizedAnywhere());
		request.setAttribute("isAuthorizationResponsibleAnywhere", rolerequestService.isAuthorizationResponsibleAnywhere(loggedInUser));
		request.setAttribute("waitingRequestsCount", waitingRequestsService.countWaitingRequests(loggedInUser));

		return true;
	}

}
