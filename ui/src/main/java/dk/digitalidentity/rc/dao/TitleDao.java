package dk.digitalidentity.rc.dao;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.Title;

public interface TitleDao extends CrudRepository<Title, String>, JpaSpecificationExecutor<Title> {

	// not really deprecated, but findAll() should only be used when we want
	// to access entities that have been deleted, so this is used as a warning
	// to the developer
	//@Deprecated
	List<Title> findAll();

	List<Title> getByActiveTrue();

	Title getByUuidAndActiveTrue(String uuid);

	List<Title> findByUuidInAndActiveTrue(Set<String> titles);

}
