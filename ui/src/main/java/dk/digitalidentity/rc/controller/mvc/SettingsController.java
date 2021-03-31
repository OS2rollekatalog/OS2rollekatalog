package dk.digitalidentity.rc.controller.mvc;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;

@RequireAdministratorRole
@Controller
public class SettingsController {
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private OrgUnitService orgUnitService;

	@GetMapping(value = "/ui/settings")
	public String settings(Model model) {
		SettingsForm settingsForm = new SettingsForm();

		settingsForm.setRequestApproveEnabled(settingsService.isRequestApproveEnabled());
		settingsForm.setItSystemMarkupEnabled(settingsService.isItSystemMarkupEnabled());
		settingsForm.setRequestApproveManagerAction(settingsService.getRequestApproveManagerAction());
		settingsForm.setServicedeskEmail(settingsService.getRequestApproveServicedeskEmail());
		settingsForm.setRemovalOfUnitRolesEmail(settingsService.getRemovalOfUnitRolesEmail());

		settingsForm.setOrganisationEventsEnabled(settingsService.isOrganisationEventsEnabled());
		settingsForm.setOuNewManagerAction(settingsService.getOuNewManagerAction());
		settingsForm.setOuNewParentAction(settingsService.getOuNewParentAction());
		settingsForm.setUserNewPositionAction(settingsService.getUserNewPositionAction());

		settingsForm.setScheduledAttestationEnabled(settingsService.isScheduledAttestationEnabled());
		settingsForm.setScheduledAttestationInterval(settingsService.getScheduledAttestationInterval());
		settingsForm.setScheduledAttestationIntervalSensitive(settingsService.getScheduledAttestationIntervalSensitive());
		settingsForm.setScheduledAttestationFilter(settingsService.getScheduledAttestationFilter());
		settingsForm.setScheduledAttestationDayInMonth(settingsService.getScheduledAttestationDayInMonth());
		
		settingsForm.setItSystemChangeEmail(settingsService.getItSystemChangeEmail());
		
		settingsForm.setReminderCount(settingsService.getReminderCount());
		settingsForm.setDaysBeforeDeadline(settingsService.getDaysBeforeDeadline());
		settingsForm.setReminderInterval(settingsService.getReminderInterval());
		settingsForm.setEmailAfterReminders(settingsService.getEmailAfterReminders());

		List<OUListForm> allOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, false))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);
		model.addAttribute("settingsForm", settingsForm);

		return "setting/settings";
	}

	@PostMapping(value = "/ui/settings")
	public String updateSettings(Model model, @ModelAttribute("settingsForm") SettingsForm settingsForm, RedirectAttributes redirectAttributes) {
		settingsService.setRequestApproveEnabled(settingsForm.isRequestApproveEnabled());
		settingsService.setItSystemMarkupEnabled(settingsForm.isItSystemMarkupEnabled());
		settingsService.setRequestApproveManagerAction(settingsForm.getRequestApproveManagerAction());
		settingsService.setRequestApproveServicedeskEmail(settingsForm.getServicedeskEmail());
		settingsService.setRemovalOfUnitRolesEmail(settingsForm.getRemovalOfUnitRolesEmail());

		settingsService.setOrganisationEventsEnabled(settingsForm.isOrganisationEventsEnabled());
		settingsService.setOuNewManagerAction(settingsForm.getOuNewManagerAction());
		settingsService.setOuNewParentAction(settingsForm.getOuNewParentAction());
		settingsService.setUserNewPositionAction(settingsForm.getUserNewPositionAction());

		// make sure sensitive interval is at least as often as the ordinary interval
		switch (settingsForm.getScheduledAttestationIntervalSensitive()) {
			case YEARLY:
				settingsForm.setScheduledAttestationIntervalSensitive(settingsForm.getScheduledAttestationInterval());
				break;
			case EVERY_HALF_YEAR:
				if (!settingsForm.getScheduledAttestationInterval().equals(CheckupIntervalEnum.YEARLY)) {
					settingsForm.setScheduledAttestationIntervalSensitive(settingsForm.getScheduledAttestationInterval());
				}
				break;
			case QUARTERLY:
				if (settingsForm.getScheduledAttestationInterval().equals(CheckupIntervalEnum.MONTHLY)) {
					settingsForm.setScheduledAttestationIntervalSensitive(settingsForm.getScheduledAttestationInterval());
				}
				break;
			case MONTHLY:
				break;
		}
		
		settingsService.setScheduledAttestationEnabled(settingsForm.isScheduledAttestationEnabled());
		settingsService.setScheduledAttestationInterval(settingsForm.getScheduledAttestationInterval());
		settingsService.setScheduledAttestationIntervalSensitive(settingsForm.getScheduledAttestationIntervalSensitive());
		settingsService.setScheduledAttestationDayInMonth(settingsForm.getScheduledAttestationDayInMonth());
		settingsService.setScheduledAttestationFilter(settingsForm.getScheduledAttestationFilter());

		settingsService.setItSystemChangeEmail(settingsForm.getItSystemChangeEmail());
		
		settingsService.setReminderCount(settingsForm.getReminderCount());
		settingsService.setDaysBeforeDeadline(settingsForm.getDaysBeforeDeadline());
		settingsService.setReminderInterval(settingsForm.getReminderInterval());
		settingsService.setEmailAfterReminders(settingsForm.getEmailAfterReminders());

		redirectAttributes.addFlashAttribute("saved", true);

		return "redirect:/ui/settings";
	}
}
