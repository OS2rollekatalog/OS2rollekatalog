package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.controller.api.exception.BadRequestException;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.controller.api.v2.AuditLogApiV2;
import dk.digitalidentity.rc.controller.api.v2.ConstraintApiV2;
import dk.digitalidentity.rc.controller.api.v2.ItSystemApiV2;
import dk.digitalidentity.rc.controller.api.v2.ManagerSubstituteApiV2;
import dk.digitalidentity.rc.controller.api.v2.UserRoleApiV2;
import dk.digitalidentity.rc.controller.api.v2.UserRoleGroupApiV2;
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
@ControllerAdvice(assignableTypes = {AuditLogApiV2.class, ConstraintApiV2.class, ItSystemApiV2.class, ManagerSubstituteApiV2.class, UserRoleApiV2.class, UserRoleGroupApiV2.class,
        UserApi.class, RoleAssignmentApi.class, TitleApi.class, OrganisationApi.class,
        ManagerSubstituteApi.class, KspCicsApi.class, ItSystemManagedApi.class, ItSystemApi.class, ConstraintApi.class})
public class ApiControllerAdvice {

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
    public ExceptionResponseAM handleRuntimeException(final RuntimeException ex, final HttpServletRequest request) {
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

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ExceptionResponseAM handleOptimisticLockingFailure(final RuntimeException ex, final HttpServletRequest request) {
        log.warn("ObjectOptimisticLockingFailureException during api call", ex);
        return ExceptionResponseAM.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .path(request.getRequestURI())
                .message(ex.getMessage())
                .build();
    }


}
