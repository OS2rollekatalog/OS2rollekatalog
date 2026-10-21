package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.rolerequest.model.entity.ItSystemPermissionView;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

public interface ItSystemPermissionViewDao extends Repository<ItSystemPermissionView, Long> {

	List<ItSystemPermissionView> findByIdIn(Collection<Long> ids);
}
