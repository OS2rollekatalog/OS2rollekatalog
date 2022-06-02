package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.opensaml.saml.common.SAMLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;

@Controller
@PropertySource("classpath:git.properties")
public class DefaultController implements ErrorController {
	private ErrorAttributes errorAttributes = new DefaultErrorAttributes();

	@Value(value = "${error.showtrace:false}")
	private boolean showStackTrace;

	@Value(value = "${git.commit.id.abbrev}")
	private String gitCommitId;

	@Value(value = "${git.build.time}")
	private String gitBuildTime;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleDao userRoleDao;

	@Autowired
	private RoleGroupDao roleGroupDao;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@RequestMapping(value = { "/" })
	public String index(Model model) {
		model.addAttribute("itSystemCount", itSystemService.count());
		model.addAttribute("userRoleCount", userRoleDao.count());
		model.addAttribute("roleGroupCount", roleGroupDao.count());

		return "index";
	}
	
	@GetMapping("/ui/rolemenu")
	public String roleIndex() {
		if (SecurityUtil.hasRole(Constants.ROLE_READ_ACCESS)) {
			return "redirect:/ui/userroles/list";
		}

		return "redirect:/ui/my";
	}
	
	@GetMapping("/ui/reportmenu")
	public String reportIndex() {
		if (SecurityUtil.hasRole(Constants.ROLE_TEMPLATE_ACCESS) || SecurityUtil.hasRole(Constants.ROLE_READ_ACCESS)) {
			return "redirect:/ui/report/templates";
		}

		if (SecurityUtil.hasRole(Constants.ROLE_SUBSTITUTE) || SecurityUtil.hasRole(Constants.ROLE_MANAGER)) {
			return "redirect:/ui/users/attestations";
		}

		return "redirect:/";
	}

	@RequestMapping(value = { "/debug" })
	public String index(Model model, HttpServletRequest request) {
		List<String> headers = new ArrayList<>();
		Enumeration<String> headerNames = request.getHeaderNames();
		
		while (headerNames.hasMoreElements()) {
			String header = headerNames.nextElement();

			StringBuilder builder = new StringBuilder();
			Enumeration<String> headers2 = request.getHeaders(header);
			while (headers2.hasMoreElements()) {
				String headerValue = headers2.nextElement();

				if (builder.length() > 0) {
					builder.append(",");
				}
				builder.append(headerValue);
			}

			headers.add(header + " = " + builder.toString());
		}

		model.addAttribute("headers", headers);

		return "debug";
	}

	@GetMapping(value = { "/info" })
	public String info(Model model) {
		model.addAttribute("gitBuildTime", gitBuildTime.substring(0, 10));
		model.addAttribute("gitCommitId", gitCommitId);
		model.addAttribute("releaseVersion", configuration.getVersion());

		return "info";
	}

	@RequestMapping(value = "/error", produces = "text/html")
	public String errorPage(Model model, HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(new ServletWebRequest(request), showStackTrace);

		// deal with SAML errors first
		Object status = body.get("status");
		if (status != null && status instanceof Integer && (Integer) status == 999) {
			Object authException = request.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);

			// handle the forward case
			if (authException == null && request.getSession() != null) {
				authException = request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
			}

			if (authException != null && authException instanceof Throwable) {
				StringBuilder builder = new StringBuilder();
				Throwable t = (Throwable) authException;

				logThrowable(builder, t, false);
				model.addAttribute("exception", builder.toString());

				if (t.getCause() != null) {
					t = t.getCause();

					// deal with the known causes for this error
					if (t instanceof SAMLException) {
						if (t.getCause() != null && t.getCause() instanceof CredentialsExpiredException) {
							model.addAttribute("cause", "EXPIRED");
						}
						else if (t.getMessage() != null && t.getMessage().contains("Response issue time is either too old or with date in the future")) {
							model.addAttribute("cause", "SKEW");
						}
						else if (t.getMessage() != null && t.getMessage().contains("urn:oasis:names:tc:SAML:2.0:status:Responder")) {
							model.addAttribute("cause", "RESPONDER");
						}
						else {
							model.addAttribute("cause", "UNKNOWN");
						}
					}
					else {
						model.addAttribute("cause", "UNKNOWN");
					}
				}

				return "samlerror";
			}
		}

		// default to ordinary error message in case error is not SAML related
		model.addAllAttributes(body);

		return "error";
	}

	private void logThrowable(StringBuilder builder, Throwable t, boolean append) {
		StackTraceElement[] stackTraceElements = t.getStackTrace();

		builder.append((append ? "Caused by: " : "") + t.getClass().getName() + ": " + t.getMessage() + "\n");
		for (int i = 0; i < 5 && i < stackTraceElements.length; i++) {
			builder.append("  ... " + stackTraceElements[i].toString() + "\n");
		}

		if (t.getCause() != null) {
			logThrowable(builder, t.getCause(), true);
		}
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

	private Map<String, Object> getErrorAttributes(WebRequest request, boolean includeStackTrace) {
		return errorAttributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());
	}
}
