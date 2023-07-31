package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.SENumber;

public interface SENumberDao extends JpaRepository<SENumber, Long> {

	List<SENumber> findAll();

}
