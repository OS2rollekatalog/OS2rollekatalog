package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.DirtyNemLoginUser;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DirtyNemLoginUserDao extends CrudRepository<DirtyNemLoginUser, Long> {
	List<DirtyNemLoginUser> findAll();
}
