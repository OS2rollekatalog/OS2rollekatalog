package dk.digitalidentity.rc.controller.mvc;

import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.service.SettingsService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.controller.mvc.viewmodel.EmailTemplateDTO;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.EmailTemplateService;

import static dk.digitalidentity.rc.dao.model.enums.EmailTemplateType.ATTESTATION_NOTIFICATION;

@RequireAdministratorRole
@Controller
public class EmailTemplateController {

	@Autowired
	private EmailTemplateService emailTemplateService;
	@Autowired
	private SettingsService settingsService;

	@GetMapping("/ui/admin/mailtemplates")
	public String editTemplate(Model model) {
		// Do not show attestation related templates if attestation is disabled
		List<EmailTemplate> templates = emailTemplateService.findFiltered(settingsService.isScheduledAttestationEnabled()
				? null
				: StringUtils.substringBefore(ATTESTATION_NOTIFICATION.name(), "_"));
		List<EmailTemplateDTO> templateDTOs = templates.stream()
				.map(t -> EmailTemplateDTO.builder()
						.id(t.getId())
						.message(t.getMessage())
						.title(t.getTitle())
						.templateTypeText(t.getTemplateType().getMessage())
						.enabled(t.isEnabled())
						.emailTemplatePlaceholders(t.getTemplateType().getEmailTemplatePlaceholders())
						.build())
				.collect(Collectors.toList());

		model.addAttribute("templates", templateDTOs);

		return "emailtemplate/edit";
	}
}
