package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.FrontPageLinkDao;
import dk.digitalidentity.rc.dao.model.FrontPageLink;
import dk.digitalidentity.rc.dao.model.enums.LinkType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@EnableCaching
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
	public List<FrontPageLink> getAllActiveFrontPageLinks() {
		return frontPageLinkDao.findByActiveTrueAndLinkType(LinkType.FRONT_PAGE_LINK);
	}
	public List<FrontPageLink> getAllGeneralLinks() {
		return frontPageLinkDao.findByActiveTrueAndLinkType(LinkType.GENERAL_PAGE_LINK);
	}

	public boolean existsByLinkStartingWith(String link) {
		return frontPageLinkDao.existsByLinkStartingWith(link);
	}

	public record GeneralLinkDTO(String url, String icon, String title) {}
	@Cacheable(value = "FrontPageCache-getGeneralLinks")
	public Set<GeneralLinkDTO> getGeneralLinks() {
		Set<GeneralLinkDTO> generalLinks = new HashSet<>();
		getAllGeneralLinks().forEach(generalLink -> {
			generalLinks.add(new GeneralLinkDTO(generalLink.getLink(), generalLink.getIcon(), generalLink.getTitle()));
		});
		return generalLinks;
	}

	@CacheEvict(value = {
		"FrontPageCache-getGeneralLinks"
	}, allEntries = true)
	public void evictCache() {
		;
	}
}

