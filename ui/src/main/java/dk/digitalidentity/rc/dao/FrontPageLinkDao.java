package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.FrontPageLink;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface FrontPageLinkDao extends CrudRepository<FrontPageLink, Long> {
	List<FrontPageLink> findAll();
	List<FrontPageLink> findByActiveTrue();
	FrontPageLink findById(long id);
}