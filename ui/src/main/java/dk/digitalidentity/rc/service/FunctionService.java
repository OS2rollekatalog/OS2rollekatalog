package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.FunctionDao;
import dk.digitalidentity.rc.dao.model.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class FunctionService {

	@Autowired
	private FunctionDao functionDao;

	public Optional<Function> findByName(String name) {
		return functionDao.findByNameIgnoreCase(name);
	}

	public Optional<Function> findByUuid(String uuid) {
		return functionDao.findById(uuid);
	}

	public List<Function> getAllActive() {
		return functionDao.findByActiveTrue();
	}

	public List<Function> getAllIncludingInactive() {
		return functionDao.findAll();
	}

	public Function save(Function function) {
		return functionDao.save(function);
	}

	public List<Function> saveAll(List<Function> functions) {
		return functionDao.saveAll(functions);
	}

	public Collection<Function> findByUuidInAndActiveTrue(Set<String> functionUuids) {
		return functionDao.findByUuidInAndActiveTrue(functionUuids);
	}
}
