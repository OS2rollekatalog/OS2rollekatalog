package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ADConfigurationDao extends CrudRepository<ADConfiguration, Long> {
	List<ADConfiguration> findByClient(Client client);
}