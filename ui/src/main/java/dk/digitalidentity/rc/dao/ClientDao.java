package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.Client;

public interface ClientDao extends CrudRepository<Client, Long> {
	List<Client> findAll();
	Client findByApiKey(String apiKey);
	Client findByName(String name);
	Client findById(long id);
	List<Client> findByClientIntegrationType(ClientIntegrationType clientIntegrationType);
	Client findByDomain(Domain domain);
}