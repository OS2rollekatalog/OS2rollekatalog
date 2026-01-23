package dk.digitalidentity.rc.service.nemlogin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.DirtyNemLoginUserDao;
import dk.digitalidentity.rc.dao.model.DirtyNemLoginUser;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentWithInfo;

@Service
public class DirtyNemLoginUserService {

	@Autowired
	private DirtyNemLoginUserDao dirtyNemLoginUserDao;
	
	@Autowired
	private UserService userService;

	public DirtyNemLoginUser save(DirtyNemLoginUser item) {
		return dirtyNemLoginUserDao.save(item);
	}

	@Transactional(readOnly = true)
	public Map<DirtyNemLoginUser, List<UserRoleAssignmentWithInfo>> findAll(List<ItSystem> itSystems) {
		Map<DirtyNemLoginUser, List<UserRoleAssignmentWithInfo>> map = new HashMap<>();

		List<DirtyNemLoginUser> users = dirtyNemLoginUserDao.findAll();
		for (DirtyNemLoginUser user : users) {
			List<UserRoleAssignmentWithInfo> assignedUserRoles = userService.getAllUserRolesAssignmentsWithInfo(user.getUser(), itSystems);
			
			// flex
			assignedUserRoles.forEach(aur -> {
				if (aur.getUserRole() != null && aur.getUserRole().getSystemRoleAssignments() != null) {
					aur.getUserRole().getSystemRoleAssignments().forEach(sra -> {
						if (sra.getConstraintValues() != null) {
							sra.getConstraintValues().forEach(cv -> {
								if (cv.getConstraintType() != null) {
									cv.getConstraintType().getEntityId();
								}
							});
						}
					});
				}
				
				if (aur.getPostponedConstraints() != null) {
					aur.getPostponedConstraints().forEach(p -> {
						if (p.getConstraintType() != null) {
							p.getConstraintType().getEntityId();
						}
						
						if (p.getSystemRole() != null) {
							p.getSystemRole().getIdentifier();
						}
					});
				}
			});

			map.put(user, assignedUserRoles);
		}

		return map;
	}

	@Transactional
	public void deleteAll(List<DirtyNemLoginUser> items) {
		dirtyNemLoginUserDao.deleteAll(items);
	}
}
