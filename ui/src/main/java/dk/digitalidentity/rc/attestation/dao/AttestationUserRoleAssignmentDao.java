package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

public interface AttestationUserRoleAssignmentDao extends CrudRepository<AttestationUserRoleAssignment, Long> {

    @Query(value = "SELECT s FROM AttestationUserRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleOuUuid=:responsibleOuUuid")
    List<AttestationUserRoleAssignment> listValidAssignmentsByResponsibleOu(@Param("validAt") final LocalDate validAt,
                                                                            @Param("responsibleOuUuid") final String responsibleOuUuid);
    @Query(value = "SELECT s FROM AttestationUserRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleOuUuid is not null AND s.responsibleOuUuid <> :responsibleOuUuid AND s.userUuid = :userUuid")
    List<AttestationUserRoleAssignment> listValidAssignmentsForUserWhereResponsibleOUIsNot(@Param("validAt") final LocalDate validAt,
                                                                                           @Param("userUuid") final String userUuid,
                                                                                           @Param("responsibleOuUuid") final String responsibleOuUuid);

    @Query(value = "SELECT s FROM AttestationUserRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleUserUuid is not null AND s.userUuid=:userUuid")
    List<AttestationUserRoleAssignment> listValidAssignmentsForUserHandledByItSystemResponsible(@Param("validAt") final LocalDate validAt,
                                                                                                @Param("userUuid") final String userUuid);

    @Query(value = "SELECT s FROM AttestationUserRoleAssignment s WHERE s.validFrom > :fromDate AND s.validFrom < :toDate AND s.responsibleOuUuid=:responsibleOuUuid")
    List<AttestationUserRoleAssignment> listAssignmentsWhichHaveBeenValidBetweenByResponsibleOu(@Param("fromDate") final LocalDate fromDate,
                                                                                                @Param("toDate") final LocalDate toDate,
                                                                                                @Param("responsibleOuUuid") final String responsibleOuUuid);

    @Query(value = "SELECT s FROM AttestationUserRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleUserUuid=:responsibleUserUuid")
    List<AttestationUserRoleAssignment> listValidAssignmentsByResponsibleUserUuid(@Param("validAt") final LocalDate validAt,
                                                                                  @Param("responsibleUserUuid") final String responsibleUserUuid);

    @Query(value = "SELECT s FROM AttestationUserRoleAssignment s WHERE s.validFrom <= :validAt AND (s.validTo > :validAt or s.validTo is null) AND s.responsibleUserUuid=:responsibleUserUuid AND s.itSystemId=:itSystemId")
    List<AttestationUserRoleAssignment> listValidAssignmentsByResponsibleUserUuidAndItSystemId(@Param("validAt") final LocalDate validAt,
                                                                                               @Param("responsibleUserUuid") final String responsibleUserUuid,
                                                                                               @Param("itSystemId") final long itSystemId);
    @Query(nativeQuery = true, value = "SELECT * FROM attestation_user_role_assignments s2 " +
            "INNER JOIN (SELECT max(s.id) as id FROM attestation_user_role_assignments s " +
            "WHERE s.valid_from <= :validAt AND (s.valid_to > :validAt or s.valid_to is null) AND s.responsible_user_uuid is not null " +
            "GROUP BY s.responsible_user_uuid, s.user_uuid, s.sensitive_role, s.it_system_id) as sub on sub.id = s2.id")
    List<AttestationUserRoleAssignment> findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem(@Param("validAt") final LocalDate validAt);

    @Query(nativeQuery = true, value = "SELECT * FROM attestation_user_role_assignments s2 " +
            "INNER JOIN (SELECT max(s.id) as id FROM attestation_user_role_assignments s " +
            "WHERE s.valid_from <= :validAt AND (s.valid_to > :validAt or s.valid_to is null) AND s.responsible_ou_uuid is not null " +
            "GROUP BY s.responsible_ou_uuid, s.user_uuid, s.sensitive_role, s.assigned_through_type) as sub on sub.id = s2.id")
    List<AttestationUserRoleAssignment> findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(@Param("validAt") final LocalDate validAt);

    @Query("SELECT a FROM AttestationUserRoleAssignment a WHERE a.itSystemId=:itSystemId AND a.validFrom<=:to AND (a.validTo > :from OR a.validTo is null)")
    Stream<AttestationUserRoleAssignment> streamAssignmentValidBetweenForItSystem(@Param("itSystemId") final Long itSystemId,
            @Param("from") final LocalDate from,
            @Param("to") final LocalDate to);

    @Query("SELECT a FROM AttestationUserRoleAssignment a WHERE a.roleOuUuid=:ouUuid AND a.validFrom<=:to AND (a.validTo > :from OR a.validTo is null)")
    Stream<AttestationUserRoleAssignment> streamAssignmentValidBetweenForRoleOu(@Param("ouUuid") final String ouUuid,
            @Param("from") final LocalDate from,
            @Param("to") final LocalDate to);

    @Query("SELECT a.id FROM AttestationUserRoleAssignment a WHERE a.responsibleOuUuid=:ouUuid AND a.validFrom<=:to AND (a.validTo > :from OR a.validTo is null)")
    List<Long> listAssignmentIdsValidBetweenForRoleOu(@Param("ouUuid") final String ouUuid,
                                                      @Param("from") final LocalDate from,
                                                      @Param("to") final LocalDate to);

    @Query("SELECT a FROM AttestationUserRoleAssignment a WHERE a.validFrom<=:to AND (a.validTo > :from OR a.validTo is null)")
    Stream<AttestationUserRoleAssignment> streamAllAssignmentValidBetween(@Param("from") final LocalDate from, @Param("to") final LocalDate to);

    @Query("SELECT a.id FROM AttestationUserRoleAssignment a WHERE a.validFrom<=:to AND (a.validTo > :from OR a.validTo is null)")
    List<Long> listAssignmentIdsValidBetween(@Param("from") final LocalDate from, @Param("to") final LocalDate to);

    List<AttestationUserRoleAssignment> findByIdIn(List<Long> ids);

}
