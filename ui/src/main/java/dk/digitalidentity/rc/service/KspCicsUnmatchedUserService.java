package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.KspCicsUnmatchedUserDao;
import dk.digitalidentity.rc.dao.model.KspCicsUnmatchedUser;

@Service
public class KspCicsUnmatchedUserService {

	@Autowired
	private KspCicsUnmatchedUserDao kspCicsUnmatchedUserDao;
	
	public void deleteByUserId(String userId) {
		kspCicsUnmatchedUserDao.deleteByUserId(userId);
	}

	public List<KspCicsUnmatchedUser> findAll() {
		return kspCicsUnmatchedUserDao.findAll();
	}
	
	public KspCicsUnmatchedUser save(KspCicsUnmatchedUser user) {
		return kspCicsUnmatchedUserDao.save(user);
	}

	public KspCicsUnmatchedUser getByUserId(String userId) {
		return kspCicsUnmatchedUserDao.getByUserId(userId);
	}
}
