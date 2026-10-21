package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurrentExceptedAssignmentDao extends JpaRepository<CurrentExceptedAssignment, Long> {
	Set<CurrentExceptedAssignment> findAllByExceptionUserUuid(String exceptionUserUuid);

	Set<CurrentExceptedAssignment> findAllByExceptionUserUuidIn(Collection<String> exceptionUserUuids);

	@Query("SELECT c.id FROM CurrentExceptedAssignment c WHERE c.assignedWhen IS NULL AND c.id > :afterId ORDER BY c.id")
	List<Long> findIdsWithoutAssignedWhenAfter(@Param("afterId") long afterId, Pageable limit);

	@Modifying
	@Query("UPDATE CurrentExceptedAssignment c SET c.assignedWhen = :assignedWhen WHERE c.id = :id AND c.assignedWhen IS NULL")
	int setAssignedWhen(@Param("id") long id, @Param("assignedWhen") LocalDateTime assignedWhen);
}
