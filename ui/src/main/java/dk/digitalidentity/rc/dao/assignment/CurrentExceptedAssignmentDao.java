package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Set;

public interface CurrentExceptedAssignmentDao extends JpaRepository<CurrentExceptedAssignment, Long> {
	Set<CurrentExceptedAssignment> findAllByExceptionUserUuid(String exceptionUserUuid);

	Set<CurrentExceptedAssignment> findAllByExceptionUserUuidIn(Collection<String> exceptionUserUuids);
}
