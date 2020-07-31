package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.KLEMapping;

public interface KLEMappingDao extends CrudRepository<KLEMapping, Long> {
	List<KLEMapping> findByCodeAndOrgUnitActiveTrue(String code);
}
