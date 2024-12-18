package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.ClientDao;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@EnableScheduling
@EnableCaching
public class ClientService {

	@Autowired
	private ClientDao clientDao;

	@Autowired
	private ClientService self;

	@Autowired
	private RoleCatalogueConfiguration roleCatalogueConfiguration;

	@PostConstruct
	private void init() {
		// TODO: remove this code once everyone is running a newer version
		if (clientDao.findAll().isEmpty()) {
			Arrays.stream(roleCatalogueConfiguration.getCustomer().getApikey()).distinct().forEach(apiKey -> {
				Client newClient = new Client();
				newClient.setName("Administrator");
				newClient.setApiKey(apiKey);
				newClient.setAccessRole(AccessRole.ADMINISTRATOR);
				newClient.setVersionStatus(VersionStatusEnum.UNKNOWN);
				clientDao.save(newClient);
			});
		}
	}

	// used by the ApiSecurityFilter class on every single API call, so caching will help a lot here
	@Cacheable(value = "clientList")
	public Client getClientByApiKey(String apiKey) {
		return clientDao.findByApiKey(apiKey);
	}

	public Client getClientByApiKeyBypassCache(String apiKey) {
		return clientDao.findByApiKey(apiKey);
	}
	
	public Client getClientByName(String name) {
		return clientDao.findByName(name);
	}

	public Client getClientById(long id) {
		return clientDao.findById(id);
	}

	@AuditLogIntercepted
	public void delete(Client client) {
		clientDao.delete(client);
	}

	@AuditLogIntercepted
	public Client save(Client client) {
		return clientDao.save(client);
	}

	public List<Client> findAll() {
		return clientDao.findAll();
	}

	// is used in thymeleaf
	public List<Client> findADSyncServices() {
		return clientDao.findByClientIntegrationType(ClientIntegrationType.AD_SYNC_SERVICE);
	}

	public Client getClientByDomain(Domain domain) {
		if (domain == null) {
			return null;
		}
		return clientDao.findByDomain(domain);
	}

	// run every 10 minutes
	@Scheduled(fixedRate = 1000 * 60 * 10)
	public void cacheClearTask() {
		self.cacheClear();
	}

	@CacheEvict(value = "clientList", allEntries = true)
	public void cacheClear() {
		; // do nothing, annotation handles actual logic
	}
}
