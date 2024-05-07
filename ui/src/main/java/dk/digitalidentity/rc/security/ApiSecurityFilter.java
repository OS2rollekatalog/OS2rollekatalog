package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

@Slf4j
public class  ApiSecurityFilter implements Filter {
	private static final String ROLE_API = "ROLE_API_";

	private ClientService clientService;

	public ApiSecurityFilter(ClientService clientService) {
		this.clientService = clientService;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// we are using a custom header instead of Authorization because the Authorization header plays very badly with the SAML filter
		String authHeader = request.getHeader("ApiKey");
		String versionHeader = request.getHeader("ClientVersion");
		if (authHeader != null) {
			Client client = clientService.getClientByApiKey(authHeader);
			if (client == null) {
				unauthorized(response, "Invalid ApiKey header", authHeader);
				return;
			}

			ArrayList<SamlGrantedAuthority> authorities = new ArrayList<>();
			switch (client.getAccessRole()) {
				case ADMINISTRATOR:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.AUDITLOG_ACCESS.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ORGANISATION.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ROLE_MANAGEMENT.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.CICS_ADMIN.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ITSYSTEM.toString()));
					break;
				case ORGANISATION:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ORGANISATION.toString()));
					break;
				case READ_ACCESS:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					break;
				case ROLE_MANAGEMENT:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ROLE_MANAGEMENT.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ITSYSTEM.toString()));
					break;
				case CICS_ADMIN:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.CICS_ADMIN.toString()));
					break;
				case VENDOR:
					// nothing yet, planned for future features where a vendor can get read access to role assignments for own it-system
					break;
				case ITSYSTEM:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.ITSYSTEM.toString()));
					break;
				case AUDITLOG_ACCESS:
					authorities.add(new SamlGrantedAuthority(ROLE_API + AccessRole.AUDITLOG_ACCESS.toString()));
					break;
			}

			String tlsVersion = request.getHeader("x-amzn-tls-version");
			tlsVersion = (tlsVersion != null) ? ((tlsVersion.length() > 64) ? (tlsVersion.substring(0, 60) + "...") : tlsVersion) : null;
			boolean clientChanged = false;

			if (tlsVersion != null && !Objects.equals(client.getTlsVersion(), tlsVersion)) {
				client.setTlsVersion(tlsVersion);
				clientChanged = true;
			}

			if (versionHeader != null && !Objects.equals(client.getVersion(), versionHeader)) {
				client.setVersion(versionHeader);
				clientChanged = true;
			}
			
			if (clientChanged) {
				Client clientFromDb = clientService.getClientById(client.getId());
				clientFromDb.setTlsVersion(client.getTlsVersion());
				clientService.save(clientFromDb);
			}
			
			SecurityUtil.loginSystemAccount(authorities, client);

			filterChain.doFilter(servletRequest, servletResponse);
		} else {
			unauthorized(response, "Missing ApiKey header", authHeader);
		}
	}

	private static void unauthorized(HttpServletResponse response, String message, String authHeader) throws IOException {
		log.warn(message + " (authHeader = " + authHeader + ")");
		response.sendError(401, message);
	}

	@Override
	public void destroy() {
		;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		;
	}
}
