package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.User;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

public interface ItSystemDao extends CrudRepository<ItSystem, Long> {
	List<ItSystem> findAll();
	List<ItSystem> findByIdentifier(String identifier);
	List<ItSystem> findByName(String name);
	List<ItSystem> findBySystemType(ItSystemType systemType);
	List<ItSystem> findBySystemTypeIn(List<ItSystemType> systemTypes);
	List<ItSystem> findBySubscribedToNotNull();
	ItSystem findByUuid(String uuid);
	ItSystem findById(long id);
	List<ItSystem> findByHiddenFalse();
	long countByDeletedFalseAndHiddenFalse();
	List<ItSystem> findByDeletedTrue();
	List<ItSystem> findByAttestationResponsible(User user);
	List<ItSystem> findByAttestationResponsibleOrSystemOwner(User user, User user2);
	List<ItSystem> findByKitosITSystemNotNull();

	long countByAttestationResponsible(User attestationResponsible);
}
