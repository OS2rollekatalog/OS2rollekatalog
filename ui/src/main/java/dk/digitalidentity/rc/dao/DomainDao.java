package dk.digitalidentity.rc.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.Domain;

public interface DomainDao extends JpaRepository<Domain, Long> {
	Domain findById(long id);
	Domain findByName(String name);
}
