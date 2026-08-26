package dk.digitalidentity.rc.rolerequest.controller.mvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.controller.mvc.xlsxView.RequestLogXlsxView;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
import dk.digitalidentity.rc.rolerequest.service.RequestLogService;
import dk.digitalidentity.rc.security.RequireNoRole;
import dk.digitalidentity.rc.service.UserRoleService;
import jakarta.servlet.http.HttpServletResponse;

@RequireNoRole
@Controller
public class RequestLogController {

	@Autowired
	private RequestLogService requestLogService;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private UserRoleService userRoleService;

	record RequestLogDTO(LocalDateTime eventTimestamp, String actingUserName, String targetUserName, String action, String roleName,
						 String itsystem, String details, String detailsJson) {
	}

	@GetMapping("/ui/request/requestlog")
	public String requestLogIndex(Model model) {
		List<RequestLogDTO> requestLogs = requestLogService.getAllNewestFirst().stream()
			.map(requestLog -> {
				String roleOrBucket;
				String systemName;

				if (requestLog.getUserRoleId() == null) {
					roleOrBucket = requestLog.getRolegroupName();
					systemName = "(rollebuket)";
				} else {
					roleOrBucket = requestLog.getRoleName();
					UserRole userRole = userRoleService.getById(requestLog.getUserRoleId());
					systemName = (userRole != null && userRole.getItSystem() != null)
						? userRole.getItSystem().getName()
						: "Ukendt System";
				}

				return new RequestLogDTO(
					requestLog.getRequestTimestamp(),
					formatUserDisplay(requestLog.getActingUsername(), requestLog.getActingUserId()),
					formatUserDisplay(requestLog.getTargetUsername(), requestLog.getTargetUserId()),
					requestLog.getRequestEvent().getMessage(),
					roleOrBucket,
					systemName,
					requestLog.getDetails(),
					requestLog.getDetailsJson()
				);
			})
			.toList();
		model.addAttribute("requestLogs", requestLogs);

		return "requestmodule/requestLog/index";
	}

	private static String formatUserDisplay(String username, String userId) {
		return userId != null ? username + " (" + userId + ")" : username;
	}

	@GetMapping("/ui/request/requestlog/download")
	public ModelAndView download(HttpServletResponse response, Locale loc) {
		Map<String, Object> model = new HashMap<>();
		List<RequestLog> requestLogs = requestLogService.getAllNewestFirst();
		model.put("logs", requestLogs);
		model.put("locale", loc);
		model.put("messagesBundle", messageSource);

		return new ModelAndView(new RequestLogXlsxView(userRoleService), model);
	}
}
