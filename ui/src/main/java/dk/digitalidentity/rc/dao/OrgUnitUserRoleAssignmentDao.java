package dk.digitalidentity.rc.dao;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface OrgUnitUserRoleAssignmentDao extends JpaRepository<OrgUnitUserRoleAssignment, Long> {
	List<OrgUnitUserRoleAssignment> findByUserRole(UserRole userRole);

	//List<OrgUnitUserRoleAssignment> findByUserRoleAndInactiveFalse(UserRole userRole);

	List<OrgUnitUserRoleAssignment> findByContainsTitles(ContainsTitles containsTitles);

	List<OrgUnitUserRoleAssignment> findByOrgUnitAndContainsTitles(OrgUnit orgUnit, ContainsTitles containsTitles);

	List<OrgUnitUserRoleAssignment> findByOrgUnitAndContainsTitlesAndInherit(OrgUnit orgUnit, ContainsTitles containsTitles, boolean inherit);


}
