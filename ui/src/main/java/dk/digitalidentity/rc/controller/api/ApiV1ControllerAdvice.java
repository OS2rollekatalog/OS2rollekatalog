package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice(assignableTypes = {UserApi.class, RoleAssignmentApi.class, TitleApi.class, OrganisationApi.class,
        ManagerSubstituteApi.class, KspCicsApi.class, ItSystemManagedApi.class, ItSystemApi.class, ConstraintApi.class})
public class ApiV1ControllerAdvice {

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ExceptionResponseAM handleInvalidRequestException(final RuntimeException ex, final HttpServletRequest request) {
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
