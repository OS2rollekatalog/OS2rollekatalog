package dk.digitalidentity.rc.controller.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.SettingsService;

@RequireAdministratorRole
@Controller
public class VikarSettingsController {

	@Autowired
	private SettingsService settingService;

	record VikarSettings (String regex) { };
	
	@GetMapping("/ui/admin/vikarsettings")
	public String getSettings(Model model) {
		model.addAttribute("settings", new VikarSettings(settingService.getVikarRegEx()));

		return "setting/vikar_settings";
	}
	
	@PostMapping("/ui/admin/vikarsettings")
	public String setSettings(Model model, VikarSettings settings) {
		settingService.setVikarRegEx(settings.regex());

		model.addAttribute("settings", new VikarSettings(settingService.getVikarRegEx()));

		return "setting/vikar_settings";
	}
}
