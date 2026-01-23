package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.FunctionDao;
import dk.digitalidentity.rc.dao.model.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
public class FunctionService {

	@Autowired
	private FunctionDao functionDao;

	public Function findByName(String name) {
		return functionDao.findByNameIgnoreCase(name);
	}

	public List<Function> getAllActive() {
		return functionDao.findByActiveTrue();
	}

	public List<Function> getAllIncludingInactive() {
		return functionDao.findAll();
	}

	public void save(Function function) {
		functionDao.save(function);
	}

	public Collection<Function> findByUuidInAndActiveTrue(Set<String> functionUuids) {
		return functionDao.findByUuidInAndActiveTrue(functionUuids);
	}
}
