package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.KitosITSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KitosITSystemDao extends CrudRepository<KitosITSystem, Long> {

	Optional<KitosITSystem> findByKitosUuid(UUID uuid);
	void deleteByKitosUuid(UUID uuid);
	List<KitosITSystem> findAll();
}
