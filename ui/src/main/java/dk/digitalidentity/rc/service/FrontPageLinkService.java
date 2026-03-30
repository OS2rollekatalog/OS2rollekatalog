package dk.digitalidentity.rc.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.FrontPageLinkDao;
import dk.digitalidentity.rc.dao.model.FrontPageLink;
import dk.digitalidentity.rc.dao.model.enums.LinkType;

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

	public List<FrontPageLink> getAllActiveFrontPageLinksSorted() {
		return getByTypeSorted(LinkType.FRONT_PAGE_LINK);
	}
	public List<FrontPageLink> getAllGeneralLinksSorted() {
		return getByTypeSorted(LinkType.GENERAL_PAGE_LINK);
	}
	public List<FrontPageLink> getByTypeSorted(LinkType linkType) {
		return frontPageLinkDao.findByActiveTrueAndLinkType(linkType)
			.stream()
			.sorted(Comparator.comparing(FrontPageLink::getSortOrder))
			.collect(Collectors.toList());
	}


	public boolean existsByLinkStartingWith(String link) {
		return frontPageLinkDao.existsByLinkStartingWith(link);
	}

	public record GeneralLinkDTO(String url, String icon, String title) {}

	public List<FrontPageLink> getAllByLinkTypeOrderedBySortOrder(LinkType linkType) {
		return frontPageLinkDao.findByLinkTypeOrderBySortOrder(linkType);
	}

	public void moveUp(long id) {
		FrontPageLink link = frontPageLinkDao.findById(id);
		if (link == null) {
			throw new IllegalArgumentException("Link not found");
		}

		List<FrontPageLink> allLinks = frontPageLinkDao
			.findByLinkTypeOrderBySortOrder(link.getLinkType());

		int currentIndex = allLinks.indexOf(link);
		if (currentIndex > 0) {
			FrontPageLink previousLink = allLinks.get(currentIndex - 1);

			// Swap sort orders
			int tempOrder = link.getSortOrder();
			link.setSortOrder(previousLink.getSortOrder());
			previousLink.setSortOrder(tempOrder);

			frontPageLinkDao.save(link);
			frontPageLinkDao.save(previousLink);
		}
	}

	public void moveDown(long id) {
		FrontPageLink link = frontPageLinkDao.findById(id);
		if (link == null) {
			throw new IllegalArgumentException("Link not found");
		}

		List<FrontPageLink> allLinks = frontPageLinkDao
			.findByLinkTypeOrderBySortOrder(link.getLinkType());

		int currentIndex = allLinks.indexOf(link);
		if (currentIndex < allLinks.size() - 1) {
			FrontPageLink nextLink = allLinks.get(currentIndex + 1);

			// Swap sort orders
			int tempOrder = link.getSortOrder();
			link.setSortOrder(nextLink.getSortOrder());
			nextLink.setSortOrder(tempOrder);

			frontPageLinkDao.save(link);
			frontPageLinkDao.save(nextLink);
		}
	}

	@Cacheable(value = "FrontPageCache-getGeneralLinks")
	public Set<GeneralLinkDTO> getGeneralLinks() {
		Set<GeneralLinkDTO> generalLinks = new LinkedHashSet<>();
		getAllGeneralLinksSorted().forEach(generalLink -> {
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
