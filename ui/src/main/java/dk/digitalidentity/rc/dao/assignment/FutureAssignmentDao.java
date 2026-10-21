package dk.digitalidentity.rc.dao.assignment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.model.assignment.FutureAssignment;

public interface FutureAssignmentDao extends JpaRepository<FutureAssignment, Long> {

	@Query("""
	    select fa from future_assignment fa
	      join fetch fa.historicAssignment ha
	     where fa.startDate <= :today
	       and ha.validTo is null
	    """)
	List<FutureAssignment> findPublishable(LocalDate today, Limit limit);

}
