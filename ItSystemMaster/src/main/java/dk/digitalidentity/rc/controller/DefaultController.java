package dk.digitalidentity.rc.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@Controller
public class DefaultController implements ErrorController {
	private ErrorAttributes errorAttributes = new DefaultErrorAttributes();

	@Value(value = "${error.showtrace:false}")
	private boolean showStackTrace;

	@RequestMapping(value = "/login")
	public String login() {
		return "login";
	}

	@RequestMapping(value = "/error", produces = "text/html")
	public String errorPage(Model model, HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request), showStackTrace);

		// default to ordinary error message in case error is not SAML related
		model.addAllAttributes(body);

		return "error";
	}

	@RequestMapping(value = "/error", produces = "application/json")
	public ResponseEntity<Map<String, Object>> errorJSON(HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request), showStackTrace);

		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		try {
			status = HttpStatus.valueOf((int) body.get("status"));
		}
		catch (Exception ex) {
			;
		}

		return new ResponseEntity<>(body, status);
	}

	@Override
	public String getErrorPath() {
		return "/error";
	}

	private Map<String, Object> getErrorAttributes(WebRequest request, boolean includeStackTrace) {
		return errorAttributes.getErrorAttributes(request, includeStackTrace);
	}
}
