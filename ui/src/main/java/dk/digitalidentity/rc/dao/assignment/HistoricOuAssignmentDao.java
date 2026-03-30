package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface HistoricOuAssignmentDao extends JpaRepository<HistoricOuAssignment, Long> {
	@Modifying
	@Query("UPDATE HistoricOuAssignment h SET h.validTo = :validTo WHERE h.ouUuid = :ouUuid AND h.roleId = :roleId AND h.roleRoleGroupId IS NULL AND h.validTo IS NULL")
	void closeOpenByOuUuidAndRoleId(@Param("ouUuid") String ouUuid, @Param("roleId") Long roleId, @Param("validTo") LocalDateTime validTo);

	@Modifying
	@Query("UPDATE HistoricOuAssignment h SET h.validTo = :validTo WHERE h.ouUuid = :ouUuid AND h.roleRoleGroupId = :roleGroupId AND h.validTo IS NULL")
	void closeOpenByOuUuidAndRoleGroupId(@Param("ouUuid") String ouUuid, @Param("roleGroupId") Long roleGroupId, @Param("validTo") LocalDateTime validTo);

	@Modifying
	@Query("UPDATE HistoricOuAssignment h SET h.validTo = :validTo WHERE h.roleRoleGroupId = :roleGroupId AND h.roleId = :roleId AND h.validTo IS NULL")
	void closeAllOpenByRoleGroupIdAndRoleId(@Param("roleGroupId") Long roleGroupId, @Param("roleId") Long roleId, @Param("validTo") LocalDateTime validTo);

	@Modifying
	@Query("UPDATE HistoricOuAssignment h SET h.validTo = :validTo WHERE h.recordHash = :recordHash AND h.validTo IS NULL")
	void closeOpenByRecordHash(@Param("recordHash") String recordHash, @Param("validTo") LocalDateTime validTo);
}
