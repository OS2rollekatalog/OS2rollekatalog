package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.PendingOrganisationUpdate;

public interface PendingOrganisationUpdateDao extends CrudRepository<PendingOrganisationUpdate, Long> {

	public List<PendingOrganisationUpdate> findAll();

}
