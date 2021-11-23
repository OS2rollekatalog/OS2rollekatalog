package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.Title;

@Service
public class TitleService {

	@Autowired
	private TitleDao titleDao;

	public Title save(Title title) {
		return titleDao.save(title);
	}

	public void save(List<Title> list) {
		titleDao.saveAll(list);
	}

	public Title getByUuid(String uuid) {
		return titleDao.getByUuidAndActiveTrue(uuid);
	}
	
	public List<Title> getAll() {
		return titleDao.getByActiveTrue();
	}
	
	@SuppressWarnings("deprecation")
	public List<Title> getAllIncludingInactive() {
		return titleDao.findAll();
	}
}
