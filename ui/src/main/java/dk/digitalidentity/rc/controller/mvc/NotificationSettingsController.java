package dk.digitalidentity.rc.controller.mvc;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.service.SettingsService;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@Controller
public class NotificationSettingsController {
	private final SettingsService settingService;

	@RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
	@GetMapping("/ui/admin/notificationsettings")
	public String editNotificationSettings(Model model) {
		// For each NotificationType check if it is enabled and create map
		Map<NotificationType, Boolean> notificationEnabledMap = Arrays
				.stream(NotificationType.values())
				.collect(Collectors.toMap(notificationType -> notificationType, notificationType -> settingService.isNotificationTypeEnabled(notificationType)));

		model.addAttribute("settings", notificationEnabledMap);

		return "setting/notification_settings";
	}
}
