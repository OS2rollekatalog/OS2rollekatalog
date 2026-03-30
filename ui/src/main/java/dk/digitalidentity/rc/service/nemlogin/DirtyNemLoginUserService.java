package dk.digitalidentity.rc.service.nemlogin;

import dk.digitalidentity.rc.dao.DirtyNemLoginUserDao;
import dk.digitalidentity.rc.dao.model.DirtyNemLoginUser;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DirtyNemLoginUserService {

	@Autowired
	private DirtyNemLoginUserDao dirtyNemLoginUserDao;

	@Autowired
	private AssignmentService assignmentService;

	public DirtyNemLoginUser save(DirtyNemLoginUser item) {
		return dirtyNemLoginUserDao.save(item);
	}

	@Transactional(readOnly = true)
	public Map<DirtyNemLoginUser, Set<CurrentAssignment>> findAll(List<ItSystem> itSystems) {
		Map<DirtyNemLoginUser, Set<CurrentAssignment>> map = new HashMap<>();

		List<DirtyNemLoginUser> users = dirtyNemLoginUserDao.findAll();
		for (DirtyNemLoginUser user : users) {
			// force-load user
			if (user.getUser() != null) {
				user.getUser().getNemloginUuid();
			}

			Set<CurrentAssignment> assignments = assignmentService.getByUserAndItSystems(user.getUser(), itSystems);

			// Eager load relations for serialization
			assignments.forEach(assignment -> {
				if (assignment.getUserRole() != null && assignment.getUserRole().getSystemRoleAssignments() != null) {
					assignment.getUserRole().getSystemRoleAssignments().forEach(sra -> {
						if (sra.getConstraintValues() != null) {
							sra.getConstraintValues().forEach(cv -> {
								if (cv.getConstraintType() != null) {
									cv.getConstraintType().getEntityId();
								}
							});
						}
					});
					
					if (assignment.getPostponedConstraints() != null) {
						assignment.getPostponedConstraints().forEach(pc -> {
							if (pc.getValue() != null) {
								pc.getValue().size();
							}
						});
					}
				}
			});

			map.put(user, assignments);
		}

		return map;
	}

	@Transactional
	public void deleteAll(List<DirtyNemLoginUser> items) {
		dirtyNemLoginUserDao.deleteAll(items);
	}
}
