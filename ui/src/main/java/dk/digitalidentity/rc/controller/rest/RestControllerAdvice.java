package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.api.exception.BadRequestException;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.controller.rest.model.ExceptionResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice(assignableTypes = {AuditLogRestController.class, OrgUnitRestController.class, EmailTemplateRestController.class,
        FrontPageConfigurationRestController.class, ItSystemRestController.class, ManagerRestController.class, MyRestController.class,
        RolegroupRestController.class, UserRestController.class, UserRoleRestController.class})
public class RestControllerAdvice {

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ExceptionResponseDTO handleInvalidRequestException(final BadRequestException ex, final HttpServletRequest request) {
        return ExceptionResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .path(request.getRequestURI())
                .message(ex.getMessage())
                .build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<ExceptionResponseAM> handleInvalidRequestException(final ResponseStatusException ex, final HttpServletRequest request) {
        return new ResponseEntity<>(ExceptionResponseAM.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(ex.getReason())
                .path(request.getRequestURI())
                .message(ex.getMessage())
                .build(),
                ex.getStatusCode());
    }

}
