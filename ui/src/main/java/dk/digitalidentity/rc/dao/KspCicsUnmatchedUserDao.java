package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.KspCicsUnmatchedUser;

public interface KspCicsUnmatchedUserDao extends CrudRepository<KspCicsUnmatchedUser, Long> {
	List<KspCicsUnmatchedUser> findAll();
	
	void deleteByUserId(String userId);

	KspCicsUnmatchedUser getByUserId(String userId);
}
