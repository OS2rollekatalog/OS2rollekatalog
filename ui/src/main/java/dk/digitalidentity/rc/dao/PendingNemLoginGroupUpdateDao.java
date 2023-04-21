package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.PendingNemLoginGroupUpdate;

public interface PendingNemLoginGroupUpdateDao extends JpaRepository<PendingNemLoginGroupUpdate, Long> {

	public List<PendingNemLoginGroupUpdate> findAll();

	public List<PendingNemLoginGroupUpdate> findByUserRoleId(long id);
	
	public List<PendingNemLoginGroupUpdate> findByFailedFalse();
}
