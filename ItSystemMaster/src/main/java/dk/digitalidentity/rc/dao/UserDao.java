package dk.digitalidentity.rc.dao;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.User;

public interface UserDao extends CrudRepository<User, Long> {
	User getByUsername(String username);
}
