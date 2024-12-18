package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttestationOuAssignmentsDao extends CrudRepository<AttestationOuRoleAssignment, Long> {

    @Query(value = "SELECT s FROM AttestationOuRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.ouUuid=:ouUuid AND s.inherited=false AND (s.exceptedTitleUuids is null or s.exceptedTitleUuids = '') ")
    List<AttestationOuRoleAssignment> listValidNotInheritedAssignmentsForOu(@Param("validAt") final LocalDate validAt, @Param("ouUuid") final String ouUuid);

    @Query(value = "SELECT s FROM AttestationOuRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.ouUuid=:ouUuid AND s.exceptedTitleUuids is not null AND LENGTH(s.exceptedTitleUuids) > 0")
    List<AttestationOuRoleAssignment> listValidAssignmentsWithExceptedTilesForOu(@Param("validAt") final LocalDate validAt, @Param("ouUuid") final String ouUuid);

    @Query(value = "SELECT s FROM AttestationOuRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleUserUuid=:responsibleUuid AND s.inherited=false")
    List<AttestationOuRoleAssignment> listValidNotInheritedAssignmentsWithResponsibleUser(@Param("validAt") final LocalDate validAt, @Param("responsibleUuid") final String responsibleUser);

    @Query(nativeQuery = true, value = "SELECT * FROM attestation_ou_role_assignments s2 " +
            "INNER JOIN (SELECT max(s.id) as sid FROM attestation_ou_role_assignments s " +
            "WHERE s.valid_from <= :validAt AND (s.valid_to > :validAt or s.valid_to is null) and s.responsible_user_uuid is not null " +
            "GROUP BY s.responsible_user_uuid, s.sensitive_role) as sub on sub.sid = s2.id")
    List<AttestationOuRoleAssignment> findValidGroupByResponsibleUserUuidAndSensitiveRole(@Param("validAt") final LocalDate validAt);

    @Query(nativeQuery = true, value = "SELECT * FROM attestation_ou_role_assignments s2 " +
            "INNER JOIN (SELECT max(s.id) as sid FROM attestation_ou_role_assignments s " +
            "WHERE s.valid_from <= :validAt AND (s.valid_to > :validAt or s.valid_to is null) AND s.responsible_ou_uuid is not null " +
            "GROUP BY s.responsible_ou_uuid, s.sensitive_role) as sub on sub.sid = s2.id")
    List<AttestationOuRoleAssignment> findValidGroupByResponsibleOuUuidAndSensitiveRole(@Param("validAt") final LocalDate validAt);

}
