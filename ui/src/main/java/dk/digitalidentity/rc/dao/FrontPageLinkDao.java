package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.FrontPageLink;
import dk.digitalidentity.rc.dao.model.enums.LinkType;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FrontPageLinkDao extends CrudRepository<FrontPageLink, Long> {
	List<FrontPageLink> findAll();
	List<FrontPageLink> findByActiveTrueAndLinkType(LinkType linkType);
	FrontPageLink findById(long id);

    boolean existsByLinkStartingWith(String link);
	List<FrontPageLink> findByLinkTypeOrderBySortOrder(LinkType linkType);

	boolean existsByLastChangedAfter(LocalDateTime timestamp);
}
