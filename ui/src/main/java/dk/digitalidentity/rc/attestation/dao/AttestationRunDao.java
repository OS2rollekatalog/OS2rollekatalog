package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.dto.AttestationRunView;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttestationRunDao extends CrudRepository<AttestationRun, Long> {

    Optional<AttestationRun> findFirstByFinishedFalseOrderByDeadlineDesc();

    Optional<AttestationRun> findByDeadlineIs(final LocalDate deadline);

    List<AttestationRun> findByFinishedFalse();

    AttestationRun findFirstByOrderByDeadlineDesc();

    AttestationRun findFirstByCreatedAtAfterOrderByCreatedAtAsc(LocalDate after);


    @Query("select r from AttestationRun r order by r.deadline desc limit :limit")
    List<AttestationRun> findLatestRuns(@Param("limit") final int limit);

    @Query("select r.id as id, r.createdAt as createdAt, r.deadline as deadline, r.sensitive as sensitive, r.extraSensitive as extraSensitive, r.finished as finished from AttestationRun r order by r.deadline desc limit :limit")
    List<AttestationRunView> findLatestRunsSimple(@Param("limit") final int limit);

    @Query("select r from AttestationRun r order by r.deadline desc")
    List<AttestationRun> findAllRunsSorted();

    List<AttestationRun> findByFinishedFalseAndDeadlineGreaterThanEqualOrderByDeadlineDesc(LocalDate deadline);
}
