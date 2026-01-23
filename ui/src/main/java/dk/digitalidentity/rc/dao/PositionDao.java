package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;

public interface PositionDao extends CrudRepository<Position, Long> {

	List<Position> findAll();

	Position getById(long id);

	List<Position> findByOrgUnit(OrgUnit ou);

	List<Position> findByTitle(Title title);

	@Query("SELECT u.uuid FROM users u JOIN positions p ON p.user = u WHERE u.deleted = false AND p.orgUnit = ?1")
	List<String> findUserUuidByOrgUnit(OrgUnit orgUnit);
}