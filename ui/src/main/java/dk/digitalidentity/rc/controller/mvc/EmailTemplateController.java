package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EmailTemplateDTO;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequireAdministratorRole
@Controller
public class EmailTemplateController {

	@Autowired
	private EmailTemplateService emailTemplateService;
	@Autowired
	private SettingsService settingsService;
	@Autowired
	private RoleCatalogueConfiguration roleCatalogueConfiguration;

	@GetMapping("/ui/admin/mailtemplates")
	public String editTemplate(Model model) {
		List<EmailTemplate> templates = emailTemplateService.findAll();
		List<EmailTemplateDTO> templateDTOs = templates.stream()
				.filter(t -> !t.getTemplateType().isAttestation())
				.map(t -> EmailTemplateDTO.builder()
						.id(t.getId())
						.message(t.getMessage())
						.title(t.getTitle())
						.templateTypeText(t.getTemplateType().getMessage())
						.enabled(t.isEnabled())
						.emailTemplatePlaceholders(t.getTemplateType().getEmailTemplatePlaceholders())
						.notes(t.getNotes())
						.allowDaysBeforeEvent(false)
						.build())
				.collect(Collectors.toList());

		model.addAttribute("templates", templateDTOs);
		model.addAttribute("page", "mail");

		return "emailtemplate/edit";
	}

	@GetMapping("/ui/admin/mailtemplates/attestation")
	public String editTemplateAttestation(Model model) {
		boolean enabled = settingsService.isScheduledAttestationEnabled();
		List<EmailTemplateDTO> templateDTOs = new ArrayList<>();
		if (enabled) {
			List<EmailTemplate> templates = emailTemplateService.findAll();
			templateDTOs = templates.stream()
					.filter(t -> t.getTemplateType().isAttestation())
					.map(t -> EmailTemplateDTO.builder()
							.id(t.getId())
							.message(t.getMessage())
							.title(t.getTitle())
							.templateTypeText(t.getTemplateType().getMessage())
							.enabled(t.isEnabled())
							.emailTemplatePlaceholders(t.getTemplateType().getEmailTemplatePlaceholders())
							.notes(t.getNotes())
							.allowDaysBeforeEventFeature(t.getTemplateType().isAllowDaysBeforeDeadline())
							.daysBeforeEvent(t.getDaysBeforeEvent())
							.build())
					.collect(Collectors.toList());
		}

		model.addAttribute("templates", templateDTOs);
		model.addAttribute("page", "attestationMail");
		model.addAttribute("disabled", !enabled);

		return "emailtemplate/edit";
	}

	@GetMapping("/ui/admin/mailtemplates/attestation/templates")
	public String editTemplatesDropdown(Model model) {
		boolean enabled = settingsService.isScheduledAttestationEnabled();
		List<EmailTemplateDTO> templateDTOs = new ArrayList<>();
		if (enabled) {
			List<EmailTemplate> templates = emailTemplateService.findAll();
			templateDTOs = templates.stream()
					.filter(t -> t.getTemplateType().isAttestation())
					.map(t -> EmailTemplateDTO.builder()
							.id(t.getId())
							.build())
					.collect(Collectors.toList());
		}

		model.addAttribute("templates", templateDTOs);
		
		return "emailtemplate/fragments/templates_dropdown :: templates_dropdown";
	}
}
