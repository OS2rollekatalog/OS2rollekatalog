package dk.digitalidentity.rc.dao;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ConstraintType;

public interface ConstraintTypeDao extends CrudRepository<ConstraintType, Long> {
	ConstraintType getByUuid(String uuid);

	ConstraintType getByEntityId(String entityId);
}
