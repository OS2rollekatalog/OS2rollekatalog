package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttestationRunDao extends CrudRepository<AttestationRun, Long> {

    Optional<AttestationRun> findFirstByFinishedFalseAndDeadlineGreaterThanEqual(final LocalDate deadline);

    Optional<AttestationRun> findByDeadlineIs(final LocalDate deadline);

    List<AttestationRun> findByFinishedFalse();

    @Query("select r from AttestationRun r order by r.deadline desc limit :limit")
    List<AttestationRun> findLatestRuns(@Param("limit") final int limit);
}
