package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.KitosITSystemDao;
import dk.digitalidentity.rc.dao.model.KitosITSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class KitosITSystemService {

	@Autowired
	private KitosITSystemDao kitosITSystemDao;

	public Optional<KitosITSystem> findByKitosUuid(UUID uuid) {
		return kitosITSystemDao.findByKitosUuid(uuid);
	}
	public Optional<KitosITSystem> findById(long id) {
		return kitosITSystemDao.findById(id);
	}

	public KitosITSystem save(KitosITSystem kitosITSystem) {
		return kitosITSystemDao.save(kitosITSystem);
	}

	public void deleteByKitosUuid(UUID itSystemUuid) {
		kitosITSystemDao.deleteByKitosUuid(itSystemUuid);
	}

	public List<KitosITSystem> getAll() {
		return kitosITSystemDao.findAll();
	}
}
