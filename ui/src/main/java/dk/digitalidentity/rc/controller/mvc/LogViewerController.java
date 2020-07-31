package dk.digitalidentity.rc.controller.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import dk.digitalidentity.rc.service.AuditLogService;

@Controller
public class LogViewerController {

	@Autowired
	private AuditLogService auditLogService;
	
	@RequestMapping(value = "/ui/logs/audit")
	public String audit(Model model) {
		model.addAttribute("entries", auditLogService.getAuditLogEntries());

		return "logs/list-audit";
	}
}
