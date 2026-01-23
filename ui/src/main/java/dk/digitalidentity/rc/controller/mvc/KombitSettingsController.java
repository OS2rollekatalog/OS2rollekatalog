package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.rc.controller.mvc.viewmodel.KombitSettingsForm;
import dk.digitalidentity.rc.service.SettingsService;

@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@Controller
public class KombitSettingsController {

	@Autowired
	private SettingsService settingsService;

	@GetMapping(value = "/ui/kombit/settings")
	public String settings(Model model) {
		KombitSettingsForm settingsForm = new KombitSettingsForm();

		settingsForm.setItSystemsHiddenByDefault(settingsService.isItSystemsHiddenByDefault());

		model.addAttribute("settingsForm", settingsForm);

		return "setting_kombit/settings";
	}

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@PostMapping(value = "/ui/kombit/settings")
	public String updateSettings(Model model, KombitSettingsForm settingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		settingsService.setItSystemsHiddenByDefault(settingsForm.isItSystemsHiddenByDefault());

		redirectAttributes.addFlashAttribute("saved", true);

		return "redirect:/ui/kombit/settings";
	}
}
