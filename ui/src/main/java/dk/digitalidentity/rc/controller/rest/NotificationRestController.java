package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.NotificationDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.NotificationView;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.Column;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireAssignerRole
public class NotificationRestController {

	@Autowired
	private NotificationDatatableDao notificationDatatableDao;
	
	@Autowired
	private NotificationService notificationService;
	
	@Autowired
	private UserService userService;

	@PostMapping("/rest/notifications/list")
	public DataTablesOutput<?> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, @RequestHeader("show-inactive") boolean showInactive) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<NotificationView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		// search for active
		for (Column column : input.getColumns()) {
			if ("active".equals(column.getData())) {
				column.getSearch().setValue(showInactive ? "false" : "true");
			}
		}

		return notificationDatatableDao.findAll(input);
	}

	@PostMapping("/rest/notifications/changeStatus")
	public ResponseEntity<?> changeStatus(@RequestHeader("id") long id, @RequestHeader("status") boolean active) {
		Notification notification = notificationService.findById(id);
		if (notification == null) {
			return ResponseEntity.notFound().build();
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return ResponseEntity.badRequest().build();
		}
		notification.setAdminName(user.getName() + " (" + user.getUserId() + ")");
		notification.setAdminUuid(user.getUuid());
		
		notification.setActive(active);
		notificationService.save(notification);

		return ResponseEntity.ok("");
	}
	
	@PostMapping("/rest/notifications/flipAssign/{id}")
	public ResponseEntity<?> flipAssign(@PathVariable("id") long id, @RequestHeader("confirm") boolean confirm) {
		Notification notification = notificationService.findById(id);
		if (notification == null) {
			return ResponseEntity.notFound().build();
		}
		
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return ResponseEntity.badRequest().build();
		}

		if (StringUtils.hasLength(notification.getAdminUuid())) {
			if (!confirm || notification.getAdminUuid().equals(user.getUuid())) {
				notification.setAdminName(null);
				notification.setAdminUuid(null);
			}
			else {
				notification.setAdminName(user.getName() + " (" + user.getUserId() + ")");
				notification.setAdminUuid(user.getUuid());
			}
		}
		else {
			notification.setAdminName(user.getName() + " (" + user.getUserId() + ")");
			notification.setAdminUuid(user.getUuid());
		}
		
		notificationService.save(notification);
		
		return ResponseEntity.ok("");
	}
}
