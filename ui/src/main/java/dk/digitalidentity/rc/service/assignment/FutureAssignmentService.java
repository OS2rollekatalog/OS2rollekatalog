package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.assignment.FutureAssignmentDao;
import dk.digitalidentity.rc.dao.model.assignment.FutureAssignment;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FutureAssignmentService {
	private final FutureAssignmentDao futureAssignmentDao;

	public FutureAssignment save(FutureAssignment futureAssignment) {
		return futureAssignmentDao.save(futureAssignment);
	}
	
	public void deleteAll(Collection<FutureAssignment> futureAssignments) {
		futureAssignmentDao.deleteAllInBatch(futureAssignments);
	}
	
	public List<FutureAssignment> getPending() {
		return futureAssignmentDao.findPublishable(LocalDate.now(), Limit.of(5000));
	}
}
