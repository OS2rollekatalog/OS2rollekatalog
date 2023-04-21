package dk.digitalidentity.rc.controller.mvc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.UserService;

@Controller
@RequireAssignerRole
public class NotificationController {
	
	@Autowired
	private NotificationService notificationService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private ResourceBundleMessageSource resourceBundle;
	
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
