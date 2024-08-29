package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

public interface AttestationSystemRoleAssignmentDao extends JpaRepository<AttestationSystemRoleAssignment, Long> {

    @Modifying
    @Query(value = "UPDATE AttestationSystemRoleAssignment s SET s.validTo=:updatedAt WHERE s.updatedAt < :updatedAt AND (s.validTo > :updatedAt or s.validTo is null)")
    int invalidateByUpdatedAtLessThan(@Param("updatedAt") final LocalDate updatedAt);

    @Query(value = "SELECT s FROM AttestationSystemRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleUserUuid=:responsibleUserUuid")
    List<AttestationSystemRoleAssignment> listValidAttestationsByResponsibleUser(@Param("validAt") final LocalDate validAt,
                                                                                 @Param("responsibleUserUuid") final String responsibleUserUuid);

    @Query(value = "SELECT s FROM AttestationSystemRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.itSystemId=:itSystemId")
    Stream<AttestationSystemRoleAssignment> streamValidAttestationsByItSystemId(@Param("validAt") final LocalDate validAt, @Param("itSystemId") final Long itSystemId);

    @Query(value = "SELECT s FROM AttestationSystemRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null)")
    Stream<AttestationSystemRoleAssignment> streamAllValidAssignments(@Param("validAt") final LocalDate validAt);

}
