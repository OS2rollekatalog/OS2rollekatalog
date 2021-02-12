package dk.digitalidentity.rc.dao;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ConstraintType;

public interface ConstraintTypeDao extends CrudRepository<ConstraintType, Long> {
	ConstraintType findByUuid(String uuid);

	ConstraintType findByEntityId(String entityId);
}
