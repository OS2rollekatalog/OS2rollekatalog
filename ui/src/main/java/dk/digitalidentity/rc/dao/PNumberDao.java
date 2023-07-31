package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.PNumber;

public interface PNumberDao extends JpaRepository<PNumber, Long> {

	List<PNumber> findAll();

	List<PNumber> findByName(String name);
}
