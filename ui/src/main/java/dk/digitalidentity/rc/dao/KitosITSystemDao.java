package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.KitosITSystem;

public interface KitosITSystemDao extends CrudRepository<KitosITSystem, Long> {

	Optional<KitosITSystem> findByKitosUuid(UUID uuid);
	void deleteByKitosUuid(UUID uuid);
	List<KitosITSystem> findAll();
}
