package dk.digitalidentity.rc.service;

import java.util.List;

import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ItSystemService {

	@Autowired
	private ItSystemDao itSystemDao;

	public List<ItSystem> getAll() {
		return itSystemDao.findAll();
	}

	public ItSystem getFirstByName(String name) {
		List<ItSystem> result = itSystemDao.findByName(name);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public ItSystem save(ItSystem itSystem) {
		return itSystemDao.save(itSystem);
	}

//	public ItSystem getById(long id) { return itSystemDao.findOne(id); }

	public ItSystem getByMasterID(String id) { return itSystemDao.findByMasterId(id); }
}