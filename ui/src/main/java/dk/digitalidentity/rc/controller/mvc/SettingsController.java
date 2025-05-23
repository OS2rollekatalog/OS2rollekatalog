package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationSettingsForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;
import dk.digitalidentity.rc.controller.validator.AttestationSettingFormValidator;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequireAdministratorRole
@Controller
public class SettingsController {
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private AttestationSettingFormValidator attestationSettingFormValidator;
	
	@Autowired
	private NotificationService notificationService;

	@InitBinder(value="attestationSettingsForm")
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(attestationSettingFormValidator);
	}

	@GetMapping(value = "/ui/settings")
	public String settings(Model model) {
		populateModel(model);
		
		return "setting/settings";
	}

	@GetMapping(value = "/ui/settings/attestation")
	public String attestationSettings(Model model) {
		populateModelAttestation(model);

		return "setting/attestation_settings";
	}

	@PostMapping(value = "/ui/settings")
	public String updateSettings(Model model, @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			//populateModel(model);
			model.addAttribute(bindingResult.getAllErrors());

			log.warn("Bad settingsform - unable to save");
			return "setting/settings";
		}
		
		boolean requestApproveBefore = settingsService.isRequestApproveEnabled();

		settingsService.setRequestApproveEnabled(settingsForm.isRequestApproveEnabled());
		settingsService.setRequestApproveServicedeskEmail(settingsForm.getServicedeskEmail());

		settingsService.setItSystemChangeEmail(settingsForm.getItSystemChangeEmail());

		redirectAttributes.addFlashAttribute("saved", true);
		
		if (!requestApproveBefore && settingsForm.isRequestApproveEnabled()) {
			Notification notification = new Notification();
			notification.setActive(true);
			notification.setCreated(new Date());
			notification.setNotificationType(NotificationType.EDIT_REQUEST_APPROVE_EMAIL_TEMPLATE);

			notificationService.save(notification);
		}

		return "redirect:/ui/settings";
	}

	@PostMapping(value = "/ui/settings/attestation")
	public String updateAttestationSettings(Model model, @Valid @ModelAttribute("attestationSettingsForm") AttestationSettingsForm attestationSettingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());

			log.warn("Bad settingsform - unable to save");
			return "setting/attestation_settings";
		}

		boolean attestationBefore = settingsService.isScheduledAttestationEnabled();

		settingsService.setAttestationChangeEmail(attestationSettingsForm.getAttestationChangeEmail());
		settingsService.setScheduledAttestationEnabled(attestationSettingsForm.isScheduledAttestationEnabled());
		settingsService.setScheduledAttestationInterval(attestationSettingsForm.getScheduledAttestationInterval());
		settingsService.setScheduledAttestationFilter(attestationSettingsForm.getScheduledAttestationFilter());
		settingsService.setADAttestationEnabled(attestationSettingsForm.isAdAttestationEnabled());
		settingsService.setFirstAttestationDate(attestationSettingsForm.getFirstAttestationDate());
		settingsService.setAttestationRequestChangesEnabled(attestationSettingsForm.isChangeRequestsEnabled());
		settingsService.setDontSendMailToManagerEnabled(attestationSettingsForm.isDontSendMailToManager());

		redirectAttributes.addFlashAttribute("saved", true);

		if (!attestationBefore && attestationSettingsForm.isScheduledAttestationEnabled()) {
			Notification notification = new Notification();
			notification.setActive(true);
			notification.setCreated(new Date());
			notification.setNotificationType(NotificationType.EDIT_ATTESTATION_EMAIL_TEMPLATE);

			notificationService.save(notification);
		}

		return "redirect:/ui/settings/attestation";
	}

	private void populateModel(Model model) {
		SettingsForm settingsForm = new SettingsForm();

		settingsForm.setRequestApproveEnabled(settingsService.isRequestApproveEnabled());
		settingsForm.setServicedeskEmail(settingsService.getRequestApproveServicedeskEmail());
		settingsForm.setItSystemChangeEmail(settingsService.getItSystemChangeEmail());

		model.addAttribute("settingsForm", settingsForm);
	}

	private void populateModelAttestation(Model model) {
		AttestationSettingsForm settingsForm = new AttestationSettingsForm();

		settingsForm.setAttestationChangeEmail(settingsService.getAttestationChangeEmail());
		settingsForm.setScheduledAttestationEnabled(settingsService.isScheduledAttestationEnabled());
		settingsForm.setScheduledAttestationInterval(settingsService.getScheduledAttestationInterval());
		settingsForm.setScheduledAttestationFilter(settingsService.getScheduledAttestationFilter());
		settingsForm.setAdAttestationEnabled(settingsService.isADAttestationEnabled());
		settingsForm.setFirstAttestationDate(settingsService.getFirstAttestationDate());
		settingsForm.setChangeRequestsEnabled(settingsService.isAttestationRequestChangesEnabled());
		settingsForm.setDontSendMailToManager(settingsService.isDontSendMailToManagerEnabled());

		List<OUListForm> allOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, false))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);
		model.addAttribute("attestationSettingsForm", settingsForm);
	}
}
