package dk.digitalidentity.rc.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalControllerAdvice")
class InternalControllerAdviceTest {

	@Mock
	private FrontPageLinkService frontPageLinkService;

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpSession session;

	@InjectMocks
	private InternalControllerAdvice advice;

	@Nested
	@DisplayName("handleIllegalArgumentException")
	class HandleIllegalArgumentExceptionTests {

		@Test
		@DisplayName("returns a JSON ProblemDetail with 400 when the request is AJAX/JSON")
		void returnsJsonProblemDetailForJsonRequest() {
			// ---- Given ---- //
			when(request.getHeader("Accept")).thenReturn(null);
			when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");
			when(request.getRequestURI()).thenReturn("/ui/rolerequest");
			final IllegalArgumentException ex = new IllegalArgumentException("This is a test");

			// ---- When ---- //
			final Object result = advice.handleIllegalArgumentException(ex, request);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ResponseEntity.class);
			final ResponseEntity<?> response = (ResponseEntity<?>) result;
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
			assertThat(((ProblemDetail) response.getBody()).getDetail()).isEqualTo("This is a test");
		}

		@Test
		@DisplayName("returns the error/default ModelAndView with 400 when the request is a plain page navigation")
		void returnsHtmlViewForNonJsonRequest() {
			// ---- Given ---- //
			when(request.getHeader("X-Requested-With")).thenReturn(null);
			when(request.getHeader("Accept")).thenReturn("text/html");
			when(request.getRequestURI()).thenReturn("/ui/rolerequest");
			final IllegalArgumentException ex = new IllegalArgumentException("This is a test");

			// ---- When ---- //
			final Object result = advice.handleIllegalArgumentException(ex, request);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ModelAndView.class);
			final ModelAndView mav = (ModelAndView) result;
			assertThat(mav.getViewName()).isEqualTo("error/default");
			assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(mav.getModel()).containsEntry("message", "This is a test");
		}
	}

	@Nested
	@DisplayName("handleNotFoundException")
	class HandleNotFoundExceptionTests {

		@Test
		@DisplayName("returns a JSON ProblemDetail with 404 when the request is AJAX/JSON")
		void returnsJsonProblemDetailForJsonRequest() {
			// ---- Given ---- //
			when(request.getHeader("Accept")).thenReturn("application/json");
			when(request.getRequestURI()).thenReturn("/ui/user/user-uuid");
			final NotFoundException ex = new NotFoundException("User not found");

			// ---- When ---- //
			final Object result = advice.handleNotFoundException(ex, request);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ResponseEntity.class);
			final ResponseEntity<?> response = (ResponseEntity<?>) result;
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
			assertThat(((ProblemDetail) response.getBody()).getDetail()).isEqualTo("User not found");
		}

		@Test
		@DisplayName("returns the error/default ModelAndView with 404 when the request is a plain page navigation")
		void returnsHtmlViewForNonJsonRequest() {
			// ---- Given ---- //
			when(request.getHeader("Accept")).thenReturn("text/html");
			when(request.getRequestURI()).thenReturn("/ui/user/user-uuid");
			final NotFoundException ex = new NotFoundException("User not found");

			// ---- When ---- //
			final Object result = advice.handleNotFoundException(ex, request);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ModelAndView.class);
			assertThat(((ModelAndView) result).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("handleAccessDeniedException")
	class HandleAccessDeniedExceptionTests {

		@Test
		@DisplayName("returns a JSON ProblemDetail with 403 when the request is AJAX/JSON")
		void returnsJsonProblemDetailForJsonRequest() {
			// ---- Given ---- //
			when(session.getAttribute("cameFromIndex")).thenReturn(null);
			when(request.getHeader("Accept")).thenReturn("application/json");
			when(request.getRequestURI()).thenReturn("/ui/settings");
			final AccessDeniedException ex = new AccessDeniedException("Denied");

			// ---- When ---- //
			final Object result = advice.handleAccessDeniedException(ex, request, session);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ResponseEntity.class);
			assertThat(((ResponseEntity<?>) result).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("redirects to /ui/my when the request came from the discovery page")
		void redirectsWhenFromDiscovery() {
			// ---- Given ---- //
			when(session.getAttribute("cameFromIndex")).thenReturn(true);
			when(request.getRequestURI()).thenReturn("/ui/settings");
			when(frontPageLinkService.existsByLinkStartingWith("/ui/settings")).thenReturn(true);
			final AccessDeniedException ex = new AccessDeniedException("Denied");

			// ---- When ---- //
			final Object result = advice.handleAccessDeniedException(ex, request, session);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ModelAndView.class);
			assertThat(((ModelAndView) result).getViewName()).isEqualTo("redirect:/ui/my");
		}

		@Test
		@DisplayName("does not redirect when cameFromIndex is set but the link is unrelated to discovery")
		void doesNotRedirectWhenNotFromDiscoveryLink() {
			// ---- Given ---- //
			when(session.getAttribute("cameFromIndex")).thenReturn(true);
			when(request.getRequestURI()).thenReturn("/ui/settings");
			when(request.getHeader("Accept")).thenReturn("text/html");
			when(frontPageLinkService.existsByLinkStartingWith("/ui/settings")).thenReturn(false);
			final AccessDeniedException ex = new AccessDeniedException("Denied");

			// ---- When ---- //
			final Object result = advice.handleAccessDeniedException(ex, request, session);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ModelAndView.class);
			assertThat(((ModelAndView) result).getViewName()).isEqualTo("error/default");
		}
	}

	@Nested
	@DisplayName("handleNotPermittedException")
	class HandleNotPermittedExceptionTests {

		@Test
		@DisplayName("uses the permission/entity message when the constraint is not mismatched")
		void usesPermissionMessageWhenNotConstraintMismatched() {
			// ---- Given ---- //
			when(session.getAttribute("cameFromIndex")).thenReturn(null);
			when(request.getHeader("Accept")).thenReturn("application/json");
			when(request.getRequestURI()).thenReturn("/ui/user");
			final NotPermittedException ex = new NotPermittedException("denied", Section.USER, Permission.READ, false);

			// ---- When ---- //
			final Object result = advice.handleNotPermittedException(ex, request, session);

			// ---- Then ---- //
			final ProblemDetail body = (ProblemDetail) ((ResponseEntity<?>) result).getBody();
			assertThat(body.getDetail()).isEqualTo("You do not have READ permission for USER access");
		}

		@Test
		@DisplayName("uses the constraint-mismatch message when the constraint is mismatched")
		void usesConstraintMismatchMessageWhenConstraintMismatched() {
			// ---- Given ---- //
			when(session.getAttribute("cameFromIndex")).thenReturn(null);
			when(request.getHeader("Accept")).thenReturn("application/json");
			when(request.getRequestURI()).thenReturn("/ui/user");
			final NotPermittedException ex = new NotPermittedException("denied", Section.USER, Permission.READ, true);

			// ---- When ---- //
			final Object result = advice.handleNotPermittedException(ex, request, session);

			// ---- Then ---- //
			final ProblemDetail body = (ProblemDetail) ((ResponseEntity<?>) result).getBody();
			assertThat(body.getDetail()).isEqualTo("Your constraints do not allow access to this resource");
		}

		@Test
		@DisplayName("redirects to /ui/my when the request came from the discovery page")
		void redirectsWhenFromDiscovery() {
			// ---- Given ---- //
			when(session.getAttribute("cameFromIndex")).thenReturn(true);
			when(request.getRequestURI()).thenReturn("/ui/user");
			when(frontPageLinkService.existsByLinkStartingWith("/ui/user")).thenReturn(true);
			final NotPermittedException ex = new NotPermittedException("denied", Section.USER, Permission.READ, false);

			// ---- When ---- //
			final Object result = advice.handleNotPermittedException(ex, request, session);

			// ---- Then ---- //
			assertThat(result).isInstanceOf(ModelAndView.class);
			assertThat(((ModelAndView) result).getViewName()).isEqualTo("redirect:/ui/my");
		}
	}
}
