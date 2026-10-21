package dk.digitalidentity.rc.service.nemlogin;

import java.io.IOException;
import java.time.LocalDateTime;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.ResponseSpec.ErrorHandler;
import org.springframework.web.client.RestClientResponseException;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.nemlogin.model.TokenResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NemLoginTokenCache {
	private ErrorHandler defaultClientErrorHandler;
	private ErrorHandler defaultServerErrorHandler;
	private String cachedToken = null;
	private LocalDateTime expires = null;
	
	@Autowired
	private RoleCatalogueConfiguration config;

	@Lazy(true)
	@Qualifier("nemLoginRestClient")
	@Autowired
	private RestClient restClient;

	public NemLoginTokenCache() {
		defaultClientErrorHandler = new ErrorHandler() {
			
			@Override
			public void handle(HttpRequest req, ClientHttpResponse res) throws IOException {
	            throw new RestClientResponseException(
                    "Client error: " + res.getStatusCode() + " : " + IOUtils.readFully(res.getBody(), res.getBody().available()),
                    res.getStatusCode(),
                    res.getStatusText(),
                    null,
                    null,
                    null
	            );
			}
		};
		
		defaultServerErrorHandler = new ErrorHandler() {
			
			@Override
			public void handle(HttpRequest req, ClientHttpResponse res) throws IOException {
	            throw new RestClientResponseException(
                    "Server error: " + res.getStatusCode() + " : " + IOUtils.readFully(res.getBody(), res.getBody().available()),
                    res.getStatusCode(),
                    res.getStatusText(),
                    null,
                    null,
                    null
	            );
			}
		};
	}

	public synchronized String fetchToken() {
		if (cachedToken != null && expires.isAfter(LocalDateTime.now())) {
			return cachedToken;
		}

		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/idmlogin/tls/authenticate";
		
		try {
			TokenResponse response = restClient.post()
		        .uri(url)
		        .retrieve()
		        .onStatus(HttpStatusCode::is4xxClientError, defaultClientErrorHandler)
		        .onStatus(HttpStatusCode::is5xxServerError, defaultServerErrorHandler)
		        .body(TokenResponse.class);

			this.cachedToken = response.getAccessToken();
			this.expires = LocalDateTime.now().plusSeconds(response.getExpiresIn() / 2);

			return this.cachedToken;
		}
		catch (Exception ex) {
			log.error("Failed to fetch token from nemloginApi", ex);
		}

		return null;
	}
	
	public void cleanUpToken() {
		this.cachedToken = null;
		this.expires = null;
	}
}
