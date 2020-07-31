package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.DirtyKspCicsUserProfile;

public interface DirtyKspCicsUserProfileDao extends CrudRepository<DirtyKspCicsUserProfile, Long> {
	List<DirtyKspCicsUserProfile> findAll(); 
}
