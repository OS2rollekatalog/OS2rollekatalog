package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.UserKLEMapping;

public interface UserKLEMappingDao extends CrudRepository<UserKLEMapping, Long> {
	List<UserKLEMapping> findByCodeAndUserDeletedFalse(String code);
}
