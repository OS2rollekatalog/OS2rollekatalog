package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
@Controller
@RequireControllerPermission(section = Section.ADVISE, permission = Permission.READ)
public class NotificationController {
	private final NotificationService notificationService;
	private final UserService userService;
	private final MessageSource messageSource;
	private final ResourceBundleMessageSource resourceBundle;
	
	@GetMapping(path = "/ui/notifications/list")
	public String listNotifications(Model model, HttpServletRequest request, Locale locale) {
		List<Notification> notifications = notificationService.findAll();

		Map<String, String> map = new HashMap<>();
		for (Notification notification : notifications) {
			map.put(notification.getNotificationType().toString(), messageSource.getMessage(notification.getNotificationType().getMessage(), null, new Locale("da-DK")));
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		model.addAttribute("userUuid", (user != null) ? user.getUuid() : "");
		
		// update badge in UI
		long count = notifications.stream().filter(t -> t.isActive()).count();
		request.getSession().setAttribute(SessionConstants.SESSION_NOTIFICATION_COUNT, count);
		
		model.addAttribute("typesMap", map);
		model.addAttribute("notificationTypes", NotificationType.getSorted(resourceBundle, locale));

		return "notifications/list";
	}
	
	@GetMapping(path = "/ui/report/notifications/view/{id}")
	public String viewNotification(Model model, @PathVariable("id") long id) {
		Notification notification = notificationService.findById(id);
		if (notification == null) {
			return "redirect:/ui/report/notifications";
		}

		model.addAttribute("notification", notification);

		return "notifications/view";
	}
}
