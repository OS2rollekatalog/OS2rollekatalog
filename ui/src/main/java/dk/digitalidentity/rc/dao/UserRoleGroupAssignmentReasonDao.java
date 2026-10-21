package dk.digitalidentity.rc.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignmentReason;

public interface UserRoleGroupAssignmentReasonDao extends JpaRepository<UserRoleGroupAssignmentReason, Long> {
}
