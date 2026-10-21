package dk.digitalidentity.rc.service;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.PositionDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.projections.UserUuidNameAndUserId;

@Service
public class PositionService {

	@Autowired
	private PositionDao positionDao;
	
	public Position save(Position position) {
		return positionDao.save(position);
	}

	public Position getById(long positionId) {
		return positionDao.getById(positionId);
	}

	public List<Position> getAll() {
		return positionDao.findAll();
	}

	public Map<String, List<Position>> getWithOrgUnitByUserUuid(Collection<User> users) {
		if (users.isEmpty()) {
			return Map.of();
		}
		return positionDao.findByUserInWithOrgUnit(users).stream()
			.collect(Collectors.groupingBy(p -> p.getUser().getUuid()));
	}
	
	public List<Position> getAllWithTitle(Title title, boolean includeDeletedUsers) {
		List<Position> positions = positionDao.findByTitle(title);
		
		if (!includeDeletedUsers) {
			positions = positions.stream().filter(p -> !p.getUser().isDeleted()).collect(Collectors.toList());
		}

		return positions;
	}

	public List<String> findUserUuidByOrgUnitAndActiveUsers(OrgUnit orgUnit) {
		return positionDao.findUserUuidByOrgUnit(orgUnit);
	}

	public List<UserUuidNameAndUserId> findUserDetailsByOrgUnit(OrgUnit orgUnit) {
		return positionDao.findUserDetailsByOrgUnit(orgUnit);
	}
	
	public List<Position> findByOrgUnit(OrgUnit ou) {
		return positionDao.findByOrgUnit(ou);
	}

	public List<Position> findAll() { return positionDao.findAll(); }
}
