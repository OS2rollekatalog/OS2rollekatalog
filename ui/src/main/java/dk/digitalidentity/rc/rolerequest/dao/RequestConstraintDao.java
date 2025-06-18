package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.rolerequest.model.entity.RequestConstraint;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RequestConstraintDao extends CrudRepository<RequestConstraint, Long> {
	RequestConstraint getById(Long id);

    @NotNull List<RequestConstraint> findAll();
}
