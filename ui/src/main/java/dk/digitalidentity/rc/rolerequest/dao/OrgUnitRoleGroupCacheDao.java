package dk.digitalidentity.rc.rolerequest.dao;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface OrgUnitRoleGroupCacheDao extends CrudRepository<OrgUnitRoleGroupCache, Long> {
	List<OrgUnitRoleGroupCache> getByOrgUnit(OrgUnit orgUnit);
	void deleteByOrgUnit(OrgUnit orgUnit);
}