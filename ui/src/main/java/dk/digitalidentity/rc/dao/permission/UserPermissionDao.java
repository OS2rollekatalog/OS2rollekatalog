package dk.digitalidentity.rc.dao.permission;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPermissionDao extends JpaRepository<UserPermission, Long> {
	List<UserPermission> findByUserUuid(String userUuid);

	@Modifying
	@Query("DELETE FROM UserPermission u WHERE u.userUuid = :userUuid")
	void deleteByUserId(@Param("userUuid") String userUuid);

	Optional<UserPermission> findByUserUuidAndSectionAndPermission(String userUuid, Section section, Permission permission);
}
