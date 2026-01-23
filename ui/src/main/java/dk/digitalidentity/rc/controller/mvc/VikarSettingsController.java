package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import dk.digitalidentity.rc.service.SettingsService;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@Controller
public class VikarSettingsController {
	private final SettingsService settingService;

	record VikarSettings (String regex) { };
	
	@GetMapping("/ui/admin/vikarsettings")
	public String getSettings(Model model) {
		model.addAttribute("settings", new VikarSettings(settingService.getVikarRegEx()));

		return "setting/vikar_settings";
	}

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@PostMapping("/ui/admin/vikarsettings")
	public String setSettings(Model model, VikarSettings settings) {
		settingService.setVikarRegEx(settings.regex());

		model.addAttribute("settings", new VikarSettings(settingService.getVikarRegEx()));

		return "setting/vikar_settings";
	}
}
