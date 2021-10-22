package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.Client;

public interface ClientDao extends CrudRepository<Client, Long> {
	List<Client> findAll();
	Client findByApiKey(String apiKey);
	Client findByName(String name);
	Client findById(long id);
}