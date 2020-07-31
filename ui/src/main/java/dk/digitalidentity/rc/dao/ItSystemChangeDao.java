package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ItSystemChange;

public interface ItSystemChangeDao extends CrudRepository<ItSystemChange, Long> {
	public List<ItSystemChange> findAll();
	public List<ItSystemChange> findBySystemRoleId(Long systemRoleId);
	public List<ItSystemChange> findByItSystemId(Long itSystemId);
}