package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.PendingKOMBITUpdate;

public interface PendingKOMBITUpdateDao extends CrudRepository<PendingKOMBITUpdate, Long> {

	public List<PendingKOMBITUpdate> findAll();

	public PendingKOMBITUpdate getByUserRoleId(long id);
}
