package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.model.ApplicationApiDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AppManagerService {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	public List<ApplicationApiDTO> getApplications() {
		RestClient restClient = RestClient.builder()
			.defaultHeader("Content-Type", "application/json")
			.build();

		String appManagerUrl = configuration.getIntegrations().getAppManager().getUrl();
		if (!appManagerUrl.endsWith("/")) {
			appManagerUrl += "/";
		}

		appManagerUrl += "applications";

		try {
			List<ApplicationApiDTO> response = restClient
				.get()
				.uri(appManagerUrl)
				.retrieve()
				.body(new ParameterizedTypeReference<List<ApplicationApiDTO>>() {});

			return response;
		}
		catch (RestClientException ex) {
			log.warn("Failed to call AppManager API.", ex);
			return null;
		}
	}
}
