package dk.digitalidentity.rc.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

public interface ItSystemDao extends CrudRepository<ItSystem, Long> {
	List<ItSystem> findAll();

	// Pessimistic write lock — serialiserer samtidige ændringer af et IT-systems
	// responsible-/owner-collections, så orphanRemoval-DELETEs ikke racer mod en
	// anden transaktion (StaleStateException: expected row count 1 but was 0).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from ItSystem s where s.id = :id")
	Optional<ItSystem> findByIdForUpdate(@Param("id") long id);

	List<ItSystem> findByIdentifier(String identifier);
	List<ItSystem> findByName(String name);
	List<ItSystem> findBySystemType(ItSystemType systemType);
	List<ItSystem> findBySystemTypeIn(List<ItSystemType> systemTypes);
	List<ItSystem> findBySubscribedToNotNull();
	ItSystem findByUuid(String uuid);
	List<ItSystem> findByHiddenFalse();
	long countByDeletedFalseAndHiddenFalse();
	List<ItSystem> findByDeletedTrue();
	List<ItSystem> findByAttestationResponsibles_User(User user);
	List<ItSystem> findByAttestationResponsibles_UserOrSystemOwners_User(User attestationUser, User systemOwnerUser);
	List<ItSystem> findBySystemOwners_User(User user);
	List<ItSystem> findByKitosITSystemNotNull();

	List<ItSystem> findByIdInAndDeletedFalse(Collection<Long> ids);
	List<ItSystem> findAllByDeletedFalse();

	long countByAttestationResponsibles_User(User user);

	Set<ItSystem> findAllByDeletedFalseAndIdIn(Collection<Long> ids);

	List<ItSystem> findByDeletedFalseAndAttestationExemptFalse();
}
