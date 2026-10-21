package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleGroupAssignmentDao extends JpaRepository<UserRoleGroupAssignment, Long> {
	List<UserRoleGroupAssignment> findByRoleGroup(RoleGroup roleGroup);
	List<UserRoleGroupAssignment> findByOrgUnitAndInactiveFalse(OrgUnit orgUnit);
	//List<UserRoleGroupAssignment> findByRoleGroupAndInactiveFalse(RoleGroup roleGroup);

	/** The source timestamp for a directly assigned role group, see UserUserRoleAssignmentDao. */
	@Query("SELECT a.assignedTimestamp FROM user_rolegroups a WHERE a.id = :id AND a.user.uuid = :userUuid AND a.roleGroup.id = :roleGroupId")
	Optional<Date> findAssignedTimestamp(@Param("id") long id, @Param("userUuid") String userUuid,
			@Param("roleGroupId") long roleGroupId);
}
