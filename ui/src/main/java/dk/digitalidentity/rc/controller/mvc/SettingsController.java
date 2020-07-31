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

		settingsForm.setOrganisationEventsEnabled(settingsService.isOrganisationEventsEnabled());
		settingsForm.setOuNewManagerAction(settingsService.getOuNewManagerAction());
		settingsForm.setOuNewParentAction(settingsService.getOuNewParentAction());
		settingsForm.setUserNewPositionAction(settingsService.getUserNewPositionAction());

		settingsForm.setScheduledAttestationEnabled(settingsService.isScheduledAttestationEnabled());
		settingsForm.setScheduledAttestationInterval(settingsService.getScheduledAttestationInterval());
		settingsForm.setScheduledAttestationFilter(settingsService.getScheduledAttestationFilter());
		settingsForm.setScheduledAttestationDayInMonth(settingsService.getScheduledAttestationDayInMonth());
		
		settingsForm.setItSystemChangeEmail(settingsService.getItSystemChangeEmail());

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

		settingsService.setOrganisationEventsEnabled(settingsForm.isOrganisationEventsEnabled());
		settingsService.setOuNewManagerAction(settingsForm.getOuNewManagerAction());
		settingsService.setOuNewParentAction(settingsForm.getOuNewParentAction());
		settingsService.setUserNewPositionAction(settingsForm.getUserNewPositionAction());

		settingsService.setScheduledAttestationEnabled(settingsForm.isScheduledAttestationEnabled());
		settingsService.setScheduledAttestationInterval(settingsForm.getScheduledAttestationInterval());
		settingsService.setScheduledAttestationDayInMonth(settingsForm.getScheduledAttestationDayInMonth());
		settingsService.setScheduledAttestationFilter(settingsForm.getScheduledAttestationFilter());

		settingsService.setItSystemChangeEmail(settingsForm.getItSystemChangeEmail());

		redirectAttributes.addFlashAttribute("saved", true);

		return "redirect:/ui/settings";
	}
}
