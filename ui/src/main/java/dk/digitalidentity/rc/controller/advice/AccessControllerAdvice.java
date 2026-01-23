package dk.digitalidentity.rc.controller.advice;

import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class AccessControllerAdvice {

	@Autowired
	private FrontPageLinkService frontPageLinkService;

    private record ErrorDTO(String timestamp,
                            int status,
                            String error,
                            String message,
                            String path,
                            String details) {
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request,
			HttpSession session) {
        String xRequestedWith = request.getHeader("X-Requested-With");

        boolean isJsonRequest = isJSONRequest(request);
        boolean isAjax = "XMLHttpRequest".equals(xRequestedWith);
		boolean isFromDiscovery	= session.getAttribute("cameFromIndex") != null && (frontPageLinkService.existsByLinkStartingWith(request.getRequestURI()) || request.getRequestURI().contains("attestation"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        ErrorDTO error = new ErrorDTO(
                LocalDateTime.now().format(formatter),
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                ex.getMessage());

        String friendlyTitle = "Adgang forbudt.";
        String friendlyMessage = "Du har ikke adgang til at tilgå denne side.";

		// Check in session to make sure we verify that the problem comes from the discovery page
		if (isFromDiscovery) {
			session.removeAttribute("cameFromIndex");
			return new ModelAndView("redirect:/ui/my");
		}

        if (isJsonRequest || isAjax) {
            return handleJsonResponse(error);
        } else {
            return handleHtmlResponse(error,friendlyTitle , friendlyMessage);
        }
    }

    @ExceptionHandler(NotPermittedException.class)
    public Object handleNotPermittedException(
            NotPermittedException ex,
            HttpServletRequest request,
			HttpSession session) {

        String xRequestedWith = request.getHeader("X-Requested-With");

		boolean isJsonRequest = isJSONRequest(request);
		boolean isAjax = "XMLHttpRequest".equals(xRequestedWith);
		boolean isFromDiscovery	= session.getAttribute("cameFromIndex") != null && (frontPageLinkService.existsByLinkStartingWith(request.getRequestURI()) || request.getRequestURI().contains("attestation"));

        String message = "You do not have "+ ex.getPermission() +" permission for " + ex.getEntity() + " access";
        if (ex.isConstraintMismatched()) {
            message = "Your constraints do not allow access to this resource";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        String friendlyTitle = "Adgang forbudt.";
        String friendlyMessage = "Du har ikke adgang til at tilgå denne side.";

        ErrorDTO error = new ErrorDTO(
                LocalDateTime.now().format(formatter),
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                message,
                request.getRequestURI(),
                ex.getMessage());

		// Check in session to make sure we verify that the problem comes from the discovery page
		if (isFromDiscovery) {
			session.removeAttribute("cameFromIndex");
			return new ModelAndView("redirect:/ui/my");
		}

        if (isJsonRequest || isAjax) {
            return handleJsonResponse(error);
        } else {
            return handleHtmlResponse(error,friendlyTitle , friendlyMessage);
        }
    }

    private boolean isJSONRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
       return acceptHeader != null &&
                acceptHeader.contains("application/json");
    }

    private ResponseEntity<Map<String, Object>> handleJsonResponse(ErrorDTO err) {

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", err.timestamp);
        errorDetails.put("status", err.status);
        errorDetails.put("error", err.error);
        errorDetails.put("message", err.message);
        errorDetails.put("path", err.path);
        errorDetails.put("details", err.details);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorDetails);
    }

    private ModelAndView handleHtmlResponse(ErrorDTO err, String friendlyTitle, String friendlyMessage) {

        ModelAndView mav = new ModelAndView("error/default");
        mav.addObject("timestamp", err.timestamp);
        mav.addObject("status", err.status);
        mav.addObject("error",err.error);
        mav.addObject("message", err.message);
        mav.addObject("path", err.path);
        mav.addObject("details", err.details);
        mav.addObject("friendlyTitle", friendlyTitle);
        mav.addObject("friendlyMessage", friendlyMessage);
        mav.setStatus(HttpStatus.FORBIDDEN);

        return mav;
    }
}
