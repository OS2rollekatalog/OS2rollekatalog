package dk.digitalidentity.rc.controller.advice;

import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles exceptions representing expected/business conditions for the internal app (MVC + internal REST
 * controllers). Does not cover the external API (see ApiControllerAdvice) or REST-specific wrapper
 * exceptions (see RestControllerAdvice). Anything not handled here falls through to Spring Boot's
 * standard /error handling (see DefaultController).
 */
@ControllerAdvice(basePackages = {
		"dk.digitalidentity.rc.controller.mvc",
		"dk.digitalidentity.rc.controller.rest",
		"dk.digitalidentity.rc.rolerequest.controller",
		"dk.digitalidentity.rc.attestation.controller.mvc",
		"dk.digitalidentity.rc.attestation.controller.rest"
})
@RequiredArgsConstructor
public class InternalControllerAdvice {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

	private final FrontPageLinkService frontPageLinkService;

	@ExceptionHandler(IllegalArgumentException.class)
	public Object handleIllegalArgumentException(final IllegalArgumentException ex, final HttpServletRequest request) {
		final ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage(), request,
				"Ugyldig forespørgsel.", "Din forespørgsel kunne ikke behandles.");

		return respond(problemDetail, request);
	}

	@ExceptionHandler(NotFoundException.class)
	public Object handleNotFoundException(final NotFoundException ex, final HttpServletRequest request) {
		final ProblemDetail problemDetail = problemDetail(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), request,
				"Ikke fundet.", "Det du leder efter kunne ikke findes.");

		return respond(problemDetail, request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public Object handleAccessDeniedException(final AccessDeniedException ex, final HttpServletRequest request, final HttpSession session) {
		if (isFromDiscovery(request, session)) {
			session.removeAttribute("cameFromIndex");
			return new ModelAndView("redirect:/ui/my");
		}

		final ProblemDetail problemDetail = problemDetail(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to access this resource", request,
				"Adgang forbudt.", "Du har ikke adgang til at tilgå denne side.");

		return respond(problemDetail, request);
	}

	@ExceptionHandler(NotPermittedException.class)
	public Object handleNotPermittedException(final NotPermittedException ex, final HttpServletRequest request, final HttpSession session) {
		if (isFromDiscovery(request, session)) {
			session.removeAttribute("cameFromIndex");
			return new ModelAndView("redirect:/ui/my");
		}

		final String message = ex.isConstraintMismatched()
				? "Your constraints do not allow access to this resource"
				: "You do not have " + ex.getPermission() + " permission for " + ex.getEntity() + " access";

		final ProblemDetail problemDetail = problemDetail(HttpStatus.FORBIDDEN, "Access denied", message, request,
				"Adgang forbudt.", "Du har ikke adgang til at tilgå denne side.");

		return respond(problemDetail, request);
	}

	private boolean isFromDiscovery(final HttpServletRequest request, final HttpSession session) {
		return session.getAttribute("cameFromIndex") != null
				&& (frontPageLinkService.existsByLinkStartingWith(request.getRequestURI()) || request.getRequestURI().contains("attestation"));
	}

	private ProblemDetail problemDetail(final HttpStatus status, final String title, final String detail, final HttpServletRequest request,
			final String friendlyTitle, final String friendlyMessage) {
		final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
		problemDetail.setTitle(title);
		problemDetail.setInstance(URI.create(request.getRequestURI()));
		problemDetail.setProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
		problemDetail.setProperty("friendlyTitle", friendlyTitle);
		problemDetail.setProperty("friendlyMessage", friendlyMessage);

		return problemDetail;
	}

	/**
	 * Decides the response shape once for every handler above: a JSON ProblemDetail body for API/AJAX
	 * requests, or the shared HTML error view otherwise.
	 */
	private Object respond(final ProblemDetail problemDetail, final HttpServletRequest request) {
		if (isJsonRequest(request)) {
			return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
		}

		final ModelAndView mav = new ModelAndView("error/default");
		mav.addObject("timestamp", problemDetail.getProperties().get("timestamp"));
		mav.addObject("status", problemDetail.getStatus());
		mav.addObject("error", problemDetail.getTitle());
		mav.addObject("message", problemDetail.getDetail());
		mav.addObject("path", problemDetail.getInstance().toString());
		mav.addObject("details", problemDetail.getDetail());
		mav.addObject("friendlyTitle", problemDetail.getProperties().get("friendlyTitle"));
		mav.addObject("friendlyMessage", problemDetail.getProperties().get("friendlyMessage"));
		mav.setStatus(HttpStatus.valueOf(problemDetail.getStatus()));

		return mav;
	}

	private boolean isJsonRequest(final HttpServletRequest request) {
		final String acceptHeader = request.getHeader("Accept");
		final boolean acceptsJson = acceptHeader != null && acceptHeader.contains("application/json");
		final boolean isApiPath = request.getRequestURI().startsWith("/api/");
		final boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));

		return acceptsJson || isApiPath || isAjax;
	}
}
