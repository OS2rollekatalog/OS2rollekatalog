package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.FrontPageLinkDao;
import dk.digitalidentity.rc.dao.model.FrontPageLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FrontPageLinkService {

	@Autowired
	private FrontPageLinkDao frontPageLinkDao;

	public FrontPageLink getById(long id) {
		return frontPageLinkDao.findById(id);
	}

	public List<FrontPageLink> getAll() {
		return frontPageLinkDao.findAll();
	}

	public FrontPageLink save(FrontPageLink frontPageLink) {
		return frontPageLinkDao.save(frontPageLink);
	}
	public void delete(FrontPageLink frontPageLink) {
		frontPageLinkDao.delete(frontPageLink);
	}
	public List<FrontPageLink> getAllActive() {
		return frontPageLinkDao.findByActiveTrue();
	}
}
