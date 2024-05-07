package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.controller.api.exception.BadRequestException;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
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
@ControllerAdvice(assignableTypes = {AuditLogApiV2.class, ItSystemApiV2.class, ManagerSubstituteApiV2.class, UserRoleApiV2.class, UserRoleGroupApiV2.class})
public class ApiV2ControllerAdvice {

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ExceptionResponseAM handleInvalidRequestException(final BadRequestException ex, final HttpServletRequest request) {
        return ExceptionResponseAM.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .path(request.getRequestURI())
                .message(ex.getMessage())
                .build();
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ExceptionResponseAM handleInvalidRequestException(final RuntimeException ex, final HttpServletRequest request) {
        log.error("Unknown exception during api call", ex);
        return ExceptionResponseAM.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
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
