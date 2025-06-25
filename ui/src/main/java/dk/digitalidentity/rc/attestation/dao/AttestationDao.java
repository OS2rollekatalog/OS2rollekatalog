package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface AttestationDao extends CrudRepository<Attestation, Long> {
    // TODO It would probably be better to look for "open" attestations and not by deadline on/after
    Optional<Attestation> findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(
            final Attestation.AttestationType attestationType, final long itSystemId, LocalDate deadlineOnOrAfter);

    List<Attestation> findByAttestationTypeAndResponsibleUserUuidOrderByDeadlineDesc(final Attestation.AttestationType type, final String userUuid);

    List<Attestation> findByAttestationTypeAndDeadlineIsGreaterThanEqual(final Attestation.AttestationType type, final LocalDate deadline);
    List<Attestation> findByAttestationTypeInAndDeadlineIsGreaterThanEqual(final List<Attestation.AttestationType> type, final LocalDate deadline);

    List<Attestation> findByAttestationTypeAndCreatedAtGreaterThanEqual(final Attestation.AttestationType type, final LocalDate createdAtAfter);

    List<Attestation> findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(final Attestation.AttestationType type, final LocalDate createdAtAfter);


    @Query("select a from Attestation a where a.attestationType=:type and a.responsibleOuUuid=:ouUuid and a.deadline >= :when")
    Optional<Attestation> findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(@Param("type") final Attestation.AttestationType type, @Param("ouUuid") final String ouUuid, @Param("when") final LocalDate when);
    Optional<Attestation> findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(final Attestation.AttestationType type, final String ouUuid);

    Attestation findFirstByAttestationTypeAndResponsibleOuUuidAndVerifiedAtIsNotNullOrderByDeadlineDesc(final Attestation.AttestationType type, final String ouUuid);

    Optional<Attestation> findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(final Attestation.AttestationType type, final Long itSystemId, final LocalDate onOrAfter);

    List<Attestation> findByAttestationTypeAndResponsibleUserUuidAndVerifiedAtIsNull(final Attestation.AttestationType type, final String responsibleUserUuid);

    Optional<Attestation> findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(final Attestation.AttestationType type, final Long itSystemId);

    @Query(value = "select att from Attestation att " +
            "where att.attestationType=:type and att.deadline = :deadlineAt and att.verifiedAt is null" +
            " and not exists (select m from AttestationMail m where m.attestation=att and m.emailType=:emailType)")
    List<Attestation> findAttestationsWhichNeedsMail(@Param("type") final Attestation.AttestationType type,
                                                     @Param("emailType") final AttestationMail.MailType emailType,
                                                     @Param("deadlineAt") final LocalDate deadlineAt);

    @Query(nativeQuery = true, value = "select a.* from attestation_attestation as a" +
            "    inner join attestation_it_system_user_attestation_entry oa on oa.attestation_id=a.id" +
            "           where a.attestation_type='IT_SYSTEM_ATTESTATION' and a.it_system_id=:itSystemId and oa.user_uuid=:userUuid and oa.created_at > :from and oa.created_at < :to")
    List<Attestation> findItSystemUserAttestationsForUser(@Param("itSystemId") final Long itSystemId, @Param("userUuid") final String userUuid,
                                                          @Param("from") final ZonedDateTime from,
                                                          @Param("to") final ZonedDateTime to);

    @Query(nativeQuery = true, value = "select a.* from attestation_attestation as a" +
            "    inner join attestation_it_system_organisation_attestation_entry oa on oa.attestation_id=a.id" +
            "           where a.attestation_type='IT_SYSTEM_ATTESTATION' and a.it_system_id=:itSystemId and oa.created_at > :from and oa.created_at < :to")
    List<Attestation> findItSystemUserAttestations(@Param("itSystemId") final Long itSystemId,
                                                   @Param("from") final ZonedDateTime from,
                                                   @Param("to") final ZonedDateTime to);

    @Query(nativeQuery = true, value = "select a.* from attestation_attestation as a" +
            "    inner join attestation_organisation_user_attestation_entry oue on oue.attestation_id=a.id" +
            "           where a.attestation_type='ORGANISATION_ATTESTATION' and a.responsible_ou_uuid=:ouUuid and oue.user_uuid=:userUuid and oue.created_at > :from and oue.created_at < :to")
    List<Attestation> findOrganisationUserAttestationsForUser(@Param("ouUuid") final String ouUuid,
                                                              @Param("userUuid") final String userUuid,
                                                              @Param("from") final ZonedDateTime from,
                                                              @Param("to") final ZonedDateTime to);

    @Query(nativeQuery = true, value = "select a.* from attestation_attestation as a" +
            "    inner join attestation_organisation_role_attestation_entry ora on ora.attestation_id=a.id" +
            "           where a.attestation_type='ORGANISATION_ATTESTATION' and a.responsible_ou_uuid=:ouUuid and ora.created_at > :from and ora.created_at < :to")
    List<Attestation> findOrganisationRoleAttestationsForOU(@Param("ouUuid") final String ouUuid,
                                                            @Param("from") final ZonedDateTime from,
                                                            @Param("to") final ZonedDateTime to);

    Attestation findByUuid(final String uuid);

    List<Attestation> findByAttestationRunIsNull();
}
