package dk.digitalidentity.rc.controller.mvc;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Valid;

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

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;
import dk.digitalidentity.rc.controller.validator.SettingFormValidator;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministratorRole
@Controller
public class SettingsController {
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private SettingFormValidator settingFormValidator;
	
	@Autowired
	private NotificationService notificationService;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(settingFormValidator);
	}

	@GetMapping(value = "/ui/settings")
	public String settings(Model model) {
		populateModel(model);
		
		return "setting/settings";
	}

	private void populateModel(Model model) {
		SettingsForm settingsForm = new SettingsForm();

		settingsForm.setRequestApproveEnabled(settingsService.isRequestApproveEnabled());
		settingsForm.setServicedeskEmail(settingsService.getRequestApproveServicedeskEmail());

		settingsForm.setItSystemChangeEmail(settingsService.getItSystemChangeEmail());

		settingsForm.setAttestationChangeEmail(settingsService.getAttestationChangeEmail());
		settingsForm.setScheduledAttestationEnabled(settingsService.isScheduledAttestationEnabled());
		settingsForm.setScheduledAttestationInterval(settingsService.getScheduledAttestationInterval());
		settingsForm.setScheduledAttestationFilter(settingsService.getScheduledAttestationFilter());
		settingsForm.setAdAttestationEnabled(settingsService.isADAttestationEnabled());
		settingsForm.setFirstAttestationDate(settingsService.getFirstAttestationDate());

		List<OUListForm> allOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, false))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);
		model.addAttribute("settingsForm", settingsForm);
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
		boolean attestationBefore = settingsService.isScheduledAttestationEnabled();

		settingsService.setRequestApproveEnabled(settingsForm.isRequestApproveEnabled());
		settingsService.setRequestApproveServicedeskEmail(settingsForm.getServicedeskEmail());

		settingsService.setItSystemChangeEmail(settingsForm.getItSystemChangeEmail());

		settingsService.setAttestationChangeEmail(settingsForm.getAttestationChangeEmail());
		settingsService.setScheduledAttestationEnabled(settingsForm.isScheduledAttestationEnabled());
		settingsService.setScheduledAttestationInterval(settingsForm.getScheduledAttestationInterval());
		settingsService.setScheduledAttestationFilter(settingsForm.getScheduledAttestationFilter());
		settingsService.setADAttestationEnabled(settingsForm.isAdAttestationEnabled());
		settingsService.setFirstAttestationDate(settingsForm.getFirstAttestationDate());

		redirectAttributes.addFlashAttribute("saved", true);
		
		if (!requestApproveBefore && settingsForm.isRequestApproveEnabled()) {
			Notification notification = new Notification();
			notification.setActive(true);
			notification.setCreated(new Date());
			notification.setNotificationType(NotificationType.EDIT_REQUEST_APPROVE_EMAIL_TEMPLATE);

			notificationService.save(notification);
		}
		
		if (!attestationBefore && settingsForm.isScheduledAttestationEnabled()) {
			Notification notification = new Notification();
			notification.setActive(true);
			notification.setCreated(new Date());
			notification.setNotificationType(NotificationType.EDIT_ATTESTATION_EMAIL_TEMPLATE);

			notificationService.save(notification);
		}

		return "redirect:/ui/settings";
	}
}
