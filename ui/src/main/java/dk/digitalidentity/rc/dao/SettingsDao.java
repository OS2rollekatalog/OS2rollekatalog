package dk.digitalidentity.rc.dao;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.Setting;

public interface SettingsDao extends CrudRepository<Setting, Long> {
	Setting findByKey(String key);
}
