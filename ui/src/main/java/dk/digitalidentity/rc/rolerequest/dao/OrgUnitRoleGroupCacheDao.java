package dk.digitalidentity.rc.rolerequest.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;

public interface OrgUnitRoleGroupCacheDao extends CrudRepository<OrgUnitRoleGroupCache, Long> {
	List<OrgUnitRoleGroupCache> getByOrgUnit(OrgUnit orgUnit);
	void deleteByOrgUnit(OrgUnit orgUnit);
	List<OrgUnitRoleGroupCache> findAll();
	void deleteByIdIn(List<Long> roleGroupCacheIdsToDelete);
}