package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ADConfigurationDao;
import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ADConfigurationService {

	@Autowired
	private ADConfigurationDao adConfigurationDao;

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
				.map(config -> StringUtils.replace(config.getErrorMessage(), "\\n", "<br>")).orElse(null);
	}

}
