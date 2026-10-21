package dk.digitalidentity.rc.dao;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.projections.UserUuidNameAndUserId;

public interface PositionDao extends CrudRepository<Position, Long> {

	List<Position> findAll();

	Position getById(long id);

	List<Position> findByOrgUnit(OrgUnit ou);

	List<Position> findByTitle(Title title);

	@Query("SELECT u.uuid FROM users u JOIN positions p ON p.user = u WHERE u.deleted = false AND p.orgUnit = ?1")
	List<String> findUserUuidByOrgUnit(OrgUnit orgUnit);

	@Query("SELECT DISTINCT new dk.digitalidentity.rc.dao.projections.UserUuidNameAndUserId(u.uuid, u.name, u.userId) "
			+ "FROM users u JOIN positions p ON p.user = u "
			+ "WHERE u.deleted = false AND p.orgUnit = ?1")
	List<UserUuidNameAndUserId> findUserDetailsByOrgUnit(OrgUnit orgUnit);

	@Query("SELECT p FROM positions p JOIN FETCH p.orgUnit WHERE p.user IN :users ORDER BY p.id")
	List<Position> findByUserInWithOrgUnit(@Param("users") Collection<User> users);

	@Query("SELECT p.orgUnit.uuid FROM positions p WHERE p.user.uuid = :userUuid")
	Set<String> findOrgUnitUuidsByUserUuid(@Param("userUuid") String userUuid);
}
