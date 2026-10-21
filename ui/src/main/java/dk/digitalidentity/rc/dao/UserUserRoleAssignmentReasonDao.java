package dk.digitalidentity.rc.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.rc.dao.model.UserUserRoleAssignmentReason;

public interface UserUserRoleAssignmentReasonDao extends JpaRepository<UserUserRoleAssignmentReason, Long> {
}
