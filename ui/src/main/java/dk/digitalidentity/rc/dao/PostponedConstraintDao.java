package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PostponedConstraintDao extends JpaRepository<PostponedConstraint, Long> {

	List<PostponedConstraint> findByUserUserRoleAssignment_User_UuidAndSystemRole_IdAndUserUserRoleAssignment_UserRole_Id(String userUuid, long systemRoleId, long userRoleId);

	Set<PostponedConstraint> findByUserUserRoleAssignment_UserAndConstraintType_EntityIdIn(User user, Collection<String> entityIds);

	Set<PostponedConstraint> findBySystemRole_IdentifierAndUserUserRoleAssignment_UserRole(String identifier, UserRole userRole);

}
