package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ConstraintType;

public interface ConstraintTypeDao extends CrudRepository<ConstraintType, Long> {
	ConstraintType findByUuid(String uuid);

	List<ConstraintType> findByEntityId(String entityId);
}
