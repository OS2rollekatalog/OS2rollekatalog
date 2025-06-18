package dk.digitalidentity.rc.rolerequest.controller.mvc;

import dk.digitalidentity.rc.rolerequest.controller.mvc.xlsxView.RequestLogXlsxView;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
import dk.digitalidentity.rc.rolerequest.service.RequestLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/ui/request/requestlog")
public class RequestLogController {

	@Autowired
	private RequestLogService requestLogService;

	@Autowired
	private MessageSource messageSource;

	record RequestLogDTO(LocalDateTime eventTimestamp, String actingUserName, String targetUserName, String action, String roleName,
						 String itsystem, String details) {
	}

	@GetMapping
	public String requestLogIndex(Model model) {

		List<RequestLogDTO> requestLogs = requestLogService.getAllNewestFirst().stream()
			.map(requestLog -> new RequestLogDTO(
				requestLog.getRequestTimestamp(),
				requestLog.getActingUser().getName(),
				requestLog.getTargetUser().getName(),
				requestLog.getRequestEvent().getMessage(),
				requestLog.getUserRole() == null ? requestLog.getRoleGroup().getName() : requestLog.getUserRole().getName(),
				requestLog.getUserRole() == null ? "(rollebuket)" : requestLog.getUserRole().getItSystem().getName(),
				requestLog.getDetails()
			))
			.toList();

		model.addAttribute("requestLogs", requestLogs);

		return "requestmodule/requestLog/index";
	}

	@GetMapping(value = "download")
	public ModelAndView download(HttpServletResponse response, Locale loc) {
		Map<String, Object> model = new HashMap<>();
		List<RequestLog> requestLogs = requestLogService.getAllNewestFirst();
		model.put("logs", requestLogs);
		model.put("locale", loc);
		model.put("messagesBundle", messageSource);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"anmodningslog.xlsx\"");

		return new ModelAndView(new RequestLogXlsxView(), model);
	}
}
