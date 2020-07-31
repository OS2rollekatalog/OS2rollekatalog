package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ItSystemChangeDao;
import dk.digitalidentity.rc.dao.model.ItSystemChange;

@Service
public class ItSystemChangeService {

	@Autowired
	private ItSystemChangeDao itSystemChangeDao;

	public ItSystemChange save(ItSystemChange entity) {
		return itSystemChangeDao.save(entity);
	}

	public List<ItSystemChange> findAll() {
		return itSystemChangeDao.findAll();
	}

	public void delete(List<ItSystemChange> entities) {
		itSystemChangeDao.deleteAll(entities);
	}

	public void deleteAll() {
		itSystemChangeDao.deleteAll();
	}
}