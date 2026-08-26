package dk.digitalidentity.rc.service.assignment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

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
		futureAssignmentDao.deleteAll(futureAssignments);
	}
	
	public List<FutureAssignment> getPending() {
		return futureAssignmentDao.findTop5000ByStartDateAfter(LocalDate.now().minusDays(1));
	}
}
