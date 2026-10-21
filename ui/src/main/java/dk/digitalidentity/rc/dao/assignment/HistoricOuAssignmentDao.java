package dk.digitalidentity.rc.dao.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface HistoricOuAssignmentDao extends JpaRepository<HistoricOuAssignment, Long> {

	@Query("SELECT h.id FROM HistoricOuAssignment h WHERE h.validTo IS NULL AND h.id > :afterId ORDER BY h.id")
	List<Long> findOpenIdsAfter(@Param("afterId") long afterId, Pageable limit);

	@EntityGraph(attributePaths = "exclusions")
	Optional<HistoricOuAssignment> findWithExclusionsById(long id);

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
	@Query("UPDATE HistoricOuAssignment h SET h.validTo = :validTo WHERE h.recordHash IN :recordHashes AND h.validTo IS NULL")
	int closeOpenByRecordHashIn(@Param("recordHashes") Collection<String> recordHashes, @Param("validTo") LocalDateTime validTo);

	@EntityGraph(attributePaths = "exclusions")
	Optional<HistoricOuAssignment> findFirstWithExclusionsByRecordHashAndValidToIsNullOrderByIdAsc(String recordHash);

	@Modifying
	@Query(nativeQuery = true, value =
		"UPDATE historic_ou_assignment h " +
		"JOIN user_roles ur ON ur.id = h.role_id " +
		"SET h.responsible_collection_id = :collectionId " +
		"WHERE h.it_system_id = :itSystemId AND h.valid_to IS NULL AND ur.role_assignment_attestation_by_attestation_responsible = true")
	void backfillResponsibleCollectionId(@Param("itSystemId") Long itSystemId, @Param("collectionId") Long collectionId);

	boolean existsByRecordHashAndValidToIsNull(String recordHash);
}
