package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ConstraintType;

public interface ConstraintTypeDao extends CrudRepository<ConstraintType, Long> {
	Optional<ConstraintType> findByUuid(String uuid);

	List<ConstraintType> findByEntityId(String entityId);

}
