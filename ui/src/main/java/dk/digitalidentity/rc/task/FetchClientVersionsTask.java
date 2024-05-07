package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import dk.digitalidentity.rc.service.AppManagerService;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.model.ApplicationApiDTO;
import dk.digitalidentity.rc.util.Version;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@EnableScheduling
@Component
@Slf4j
public class FetchClientVersionsTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private ClientService clientService;

	@Autowired
	private FetchClientVersionsTask self;

	@Autowired
	private AppManagerService appManagerService;

	// run every hour
	@Scheduled(fixedDelay = 60 * 60 * 1000)
	public void fetchClientVersions() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		if (!StringUtils.hasLength(configuration.getIntegrations().getAppManager().getUrl())) {
			return;
		}

		self.executeTask();
	}

	@Transactional(rollbackOn = Exception.class)
	public void executeTask() {
		List<ApplicationApiDTO> extClients = appManagerService.getApplications();
		if (extClients == null) {
			return;
		}

		List<Client> dbClients = clientService.findAll().stream().filter(c -> !org.apache.commons.lang3.StringUtils.isBlank(c.getApplicationIdentifier())).collect(Collectors.toList());
		for (Client client : dbClients) {

			// find matching client in api call
			ApplicationApiDTO clientDTO = extClients.stream().filter(c -> Objects.equals(c.getIdentifier(), client.getApplicationIdentifier())).findAny().orElse(null);
			if (clientDTO != null) {

				// update client
				client.setNewestVersion(clientDTO.getNewestVersion());
				client.setMinimumVersion(clientDTO.getMinimumVersion());

				if (client.getVersion() != null && client.getNewestVersion() != null && client.getMinimumVersion() != null) {
					if (org.apache.commons.lang3.StringUtils.isBlank(client.getVersion())) {
						client.setVersionStatus(VersionStatusEnum.UNKNOWN);
					}
					else {
						Version currentVersion = new Version(client.getVersion());
						Version newestVersion = new Version(client.getNewestVersion());
						Version minimumVersion = new Version(client.getMinimumVersion());

						if (currentVersion.equals(newestVersion)) {
							client.setVersionStatus(VersionStatusEnum.NEWEST);
						}
						else if (currentVersion.compareTo(minimumVersion) < 0) {
							client.setVersionStatus(VersionStatusEnum.OUTDATED);
							log.warn("Client with id " + client.getId() + " and name " + client.getName() + " is running an outdated version of " + client.getApplicationIdentifier());
						}
						else {
							client.setVersionStatus(VersionStatusEnum.UPDATABLE);
						}
					}
				}

				clientService.save(client);
			}
			else {
				log.error("Client id: " + client.getId() + " name: " + client.getName() + " has ApplicationIdentifier " + client.getApplicationIdentifier() + " but identifier was not found in API call to AppManager.");
			}
		}
	}

}