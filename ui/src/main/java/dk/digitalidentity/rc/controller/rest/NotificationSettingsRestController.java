package dk.digitalidentity.rc.controller.rest;

import java.util.Map;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.rest.model.NotificationSettingsDTO;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.SettingsService;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@RestController
public class NotificationSettingsRestController {
    private final SettingsService settingService;
    private final NotificationService notificationService;

    @RequirePermission(section = Section.CONFIG, permission = Permission.UPDATE)
    @PostMapping(value = "/rest/admin/notifications/settings")
    @ResponseBody
    public HttpEntity<String> saveSettings(@RequestBody NotificationSettingsDTO settingsDTO) {
        for (Map.Entry<NotificationType, Boolean> entry : settingsDTO.getNotificationTypes().entrySet()) {
            settingService.setNotificationTypeEnabled(entry.getKey(), entry.getValue());
        }

        // delete already created of disabled types
        if (settingsDTO.isDeleteAlreadyCreated()) {
            for (NotificationType type : NotificationType.values()) {
                if (!settingService.isNotificationTypeEnabled(type)) {
                    notificationService.deleteAllByNotificationType(type);
                }
            }
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
