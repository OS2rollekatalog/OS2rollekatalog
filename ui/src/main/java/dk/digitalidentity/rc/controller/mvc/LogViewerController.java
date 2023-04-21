package dk.digitalidentity.rc.controller.mvc;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.controller.mvc.xlsview.AuditLogXlsxView;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.AuditLogService;

@RequireAdministratorRole
@Controller
public class LogViewerController {
	
	@Autowired
	private AuditLogService auditLogService;

	@Autowired
	private MessageSource messageSource;

	@RequestMapping(value = "/ui/logs/audit")
	public String audit(Model model) {
		return "logs/list-audit";
	}
	
	@RequestMapping(value = "/ui/logs/audit/download")
	public ModelAndView download(HttpServletResponse response, Locale loc) {
		Map<String, Object> model = new HashMap<>();
		model.put("logs", auditLogService.downloadAuditLog());
		model.put("locale", loc);
		model.put("messagesBundle", messageSource);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"auditlog.xlsx\"");

		return new ModelAndView(new AuditLogXlsxView(), model);
	}
}
