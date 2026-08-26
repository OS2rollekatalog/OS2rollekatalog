package dk.digitalidentity.rc.dao.assignment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.assignment.FutureAssignment;

public interface FutureAssignmentDao extends JpaRepository<FutureAssignment, Long> {

	List<FutureAssignment> findTop5000ByStartDateAfter(LocalDate cutoff);

}
