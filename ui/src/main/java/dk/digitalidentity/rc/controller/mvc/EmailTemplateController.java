package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.EmailTemplateDTO;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.RepeatingPartDescriptor;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@Controller
public class EmailTemplateController {
	private final EmailTemplateService emailTemplateService;
	private final SettingsService settingsService;

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@GetMapping("/ui/admin/mailtemplates")
	public String list(Model model) {
		List<EmailTemplate> allTemplates = emailTemplateService.findAll();

		List<EmailTemplateDTO> generalTemplates = allTemplates.stream()
			.filter(t -> !t.getTemplateType().isAttestation() && !t.getTemplateType().isRequest())
			.map(t -> toListDto(t))
			.collect(Collectors.toList());

		boolean attestationEnabled = settingsService.isScheduledAttestationEnabled();
		List<EmailTemplateDTO> attestationTemplates = attestationEnabled
			? allTemplates.stream()
			.filter(t -> t.getTemplateType().isAttestation())
			.map(t -> toListDto(t))
			.collect(Collectors.toList())
			: Collections.emptyList();

		boolean requestEnabled = settingsService.isRequestApproveEnabled();
		List<EmailTemplateDTO> requestTemplates = requestEnabled
			? allTemplates.stream()
			.filter(t -> t.getTemplateType().isRequest())
			.map(t -> toListDto(t))
			.collect(Collectors.toList())
			: Collections.emptyList();

		model.addAttribute("generalTemplates", generalTemplates);
		model.addAttribute("attestationTemplates", attestationTemplates);
		model.addAttribute("attestationEnabled", attestationEnabled);
		model.addAttribute("requestTemplates", requestTemplates);
		model.addAttribute("requestEnabled", requestEnabled);

		return "emailtemplate/list";
	}

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@GetMapping("/ui/admin/mailtemplates/{id}/edit")
	public String edit(@PathVariable long id, Model model) {
		EmailTemplate template = emailTemplateService.findById(id);
		if (template == null) {
			return "redirect:/ui/admin/mailtemplates";
		}

		RepeatingPartDescriptor repeatingPart = template.getTemplateType().getRepeatingPart();
		EmailTemplateDTO dto = EmailTemplateDTO.builder()
			.id(template.getId())
			.title(template.getTitle())
			.message(template.getMessage())
			.templateTypeText(template.getTemplateType().getMessage())
			.templateTypeName(emailTemplateService.getTemplateName(template.getId()))
			.enabled(template.isEnabled())
			.emailTemplatePlaceholders(template.getTemplateType().getEmailTemplatePlaceholders())
			.notes(template.getNotes())
			.allowDaysBeforeEventFeature(template.getTemplateType().isAllowDaysBeforeDeadline())
			.daysBeforeEvent(template.getDaysBeforeEvent() != null ? template.getDaysBeforeEvent() : 0)
			.hasRepeatingPart(template.getTemplateType().hasRepeatingPart())
			.hasNestedRepeatingPart(template.getTemplateType().hasNestedRepeatingPart())
			.repeatingPart(template.getRepeatingPart())
			.nestedRepeatingPart(template.getNestedRepeatingPart())
			.repeatingPartPlaceholders(repeatingPart != null ? repeatingPart.placeholders() : Collections.emptyList())
			.nestedRepeatingPartPlaceholders(repeatingPart != null && repeatingPart.nested() != null ? repeatingPart.nested().placeholders() : Collections.emptyList())
			.build();

		model.addAttribute("template", dto);

		return "emailtemplate/edit";
	}

	private EmailTemplateDTO toListDto(EmailTemplate template) {
		return EmailTemplateDTO.builder()
			.id(template.getId())
			.title(template.getTitle())
			.templateTypeName(emailTemplateService.getTemplateName(template.getId()))
			.category(template.getTemplateType().getCategory())
			.enabled(template.isEnabled())
			.build();
	}
}
