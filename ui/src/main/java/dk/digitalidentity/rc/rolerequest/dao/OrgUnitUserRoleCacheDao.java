package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitUserRoleCache;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface OrgUnitUserRoleCacheDao extends CrudRepository<OrgUnitUserRoleCache, Long> {
	void deleteByOrgUnit(OrgUnit orgUnit);

	List<OrgUnitUserRoleCache> getByOrgUnit(OrgUnit orgUnit);
}
