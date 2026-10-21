package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ADConfigurationDao;
import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.json.ADConfigurationJSON;
import dk.digitalidentity.rc.util.UuidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ADConfigurationService {

	@Autowired
	private ADConfigurationDao adConfigurationDao;

	@Autowired
	private ClientService clientService;

	@Autowired
	private DomainService domainService;

	public void save(ADConfiguration newADConfiguration) {
		adConfigurationDao.save(newADConfiguration);
	}

	public ADConfiguration getByClient(Client client) {
		List<ADConfiguration> configurations = adConfigurationDao.findByClient(client);
		return configurations.stream()
				.max(Comparator.comparingInt(ADConfiguration::getVersion))
				.orElse(null);
	}

	public String hasError(Client client) {
	    final ADConfiguration adConfiguration = getByClient(client);
	    return Optional.ofNullable(adConfiguration)
	            .map(ADConfiguration::getErrorMessage)
	            .map(msg -> msg.replace("\\n", "<br>"))
	            .orElse(null);
	}

	/**
	 * BackSync links AD groups (system roles) to job function roles through a shared identifier (a UUID),
	 * and overwrites the name/description of the linked job function role if the AD configuration says so.
	 * In that case the name/description cannot be maintained in the role catalogue.
	 */
	public boolean isNameAndDescriptionMaintainedByBackSync(UserRole userRole) {
		ItSystem itSystem = userRole.getItSystem();
		if (itSystem == null || itSystem.getSystemType() != ItSystemType.AD) {
			return false;
		}

		if (userRole.getIdentifier() == null || !UuidUtil.isUuid(userRole.getIdentifier())) {
			return false;
		}

		Domain domain = itSystem.getDomain() != null ? itSystem.getDomain() : domainService.getPrimaryDomain();
		Client client = clientService.getClientByDomain(domain);
		if (client == null) {
			return false;
		}

		ADConfiguration adConfiguration = getByClient(client);
		ADConfigurationJSON json = adConfiguration != null ? adConfiguration.getJson() : null;

		return json != null && json.isBackSyncFeatureEnabled() && json.isBackSyncFeatureMaintainDescriptionAndName();
	}
}
