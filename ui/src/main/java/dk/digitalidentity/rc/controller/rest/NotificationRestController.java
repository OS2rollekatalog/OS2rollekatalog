package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.NotificationDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.NotificationView;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.NotificationEntityType;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequireControllerPermission(section = Section.ADVISE, permission = Permission.READ)
public class NotificationRestController {
	private final NotificationDatatableDao notificationDatatableDao;
	private final NotificationService notificationService;
	private final UserService userService;
	private final UserPermissionContext userPermissionContext;

	private final Section permissionEntity = Section.ADVISE;
	private final OrgUnitService orgUnitService;

	public record NotificactionViewDTO(long id, boolean active, String affectedEntityUuid,
									   NotificationEntityType affectedEntityType, String affectedEntityName,
									   NotificationType notificationType, String created, String lastUpdated,
									   String message, String adminUuid, String adminName,
									   ItemPermissionDTO allowedActions, boolean canReadAffectedEntity
	) { }
	@PostMapping("/rest/notifications/list")
	public DataTablesOutput<NotificactionViewDTO> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult, @RequestHeader("show-inactive") boolean showInactive) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<NotificactionViewDTO> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		// search for active
		for (Column column : input.getColumns()) {
			if ("active".equals(column.getData())) {
				column.getSearch().setValue(showInactive ? "false" : "true");
			}
		}

		return mapToDTO(notificationDatatableDao.findAll(input));
	}

	private DataTablesOutput<NotificactionViewDTO> mapToDTO(DataTablesOutput<NotificationView> output) {
		Set<String> ouReadConstraints = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.READ).getConstrainedOUUuids();
		Set<String> userReadConstraints = userPermissionContext.getConstraint(Section.USER, Permission.READ).getConstrainedOUUuids();

		DataTablesOutput<NotificactionViewDTO> dto = new DataTablesOutput<>();
		dto.setSearchPanes(output.getSearchPanes());
		dto.setError(output.getError());
		dto.setRecordsFiltered(output.getRecordsFiltered());
		dto.setRecordsTotal(output.getRecordsTotal());
		dto.setDraw(output.getDraw());
		dto.setData(output.getData().stream().map(n -> {
				boolean canReadAffectedEntity = true;
				if (n.getAffectedEntityType() == NotificationEntityType.USERS && userReadConstraints != null) {
					if (userReadConstraints.isEmpty()) {
						canReadAffectedEntity = false;
					}
					// check if users read access constraints to users contains this user
					User user = userService.getOptionalByUuid(n.getAffectedEntityUuid()).orElse(null);
					if (user != null) {
						Set<String> userOuUuids = orgUnitService.getOrgUnitsForUser(user).stream().map(OrgUnit::getUuid).collect(Collectors.toSet());
						canReadAffectedEntity = userReadConstraints.stream().anyMatch(uuid -> !userOuUuids.contains(uuid));
					}
				} else if (n.getAffectedEntityType() == NotificationEntityType.OUS && ouReadConstraints != null) {
					if (ouReadConstraints.isEmpty()) {
						canReadAffectedEntity = false;
					}
					// Check if users read access constraint to ous contains this ou
					canReadAffectedEntity = ouReadConstraints.contains(n.getAffectedEntityUuid());
				}
				return new NotificactionViewDTO(
					n.getId(),
					n.isActive(),
					n.getAffectedEntityUuid(),
					n.getAffectedEntityType(),
					n.getAffectedEntityName(),
					n.getNotificationType(),
					n.getCreated(),
					n.getLastUpdated(),
					n.getMessage(),
					n.getAdminUuid(),
					n.getAdminName(),
					new ItemPermissionDTO(
						userPermissionContext.hasPermission(permissionEntity, Permission.CREATE),
						userPermissionContext.hasPermission(permissionEntity, Permission.READ),
						userPermissionContext.hasPermission(permissionEntity, Permission.UPDATE),
						userPermissionContext.hasPermission(permissionEntity, Permission.DELETE)
					),
					canReadAffectedEntity
				);
			}
		).toList());
		return dto;
	}

	@RequirePermission(section = Section.ADVISE, permission = Permission.DELETE)
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

	@RequirePermission(section = Section.ADVISE, permission = Permission.UPDATE)
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
