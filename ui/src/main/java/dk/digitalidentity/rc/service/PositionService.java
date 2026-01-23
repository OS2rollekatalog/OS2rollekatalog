package dk.digitalidentity.rc.service;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.PositionDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;

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
	
	public List<Position> findByOrgUnit(OrgUnit ou) {
		return positionDao.findByOrgUnit(ou);
	}

	public List<Position> findAll() { return positionDao.findAll(); }
}
