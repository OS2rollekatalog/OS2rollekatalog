package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class AttestationReportContextService {

    @Autowired
    private AttestationDao attestationDao;

    @Getter
    @Builder
    public static class AttestationReportContext {
        private List<Attestation> itSystemUserAttestations;
        private List<Attestation> itSystemRolesAttestations;
        private List<Attestation> organisationRolesAttestations;
    }

    /**
     * The audit report is pulled for one run, so only that run's attestations may set a status on its rows.
     */
    public AttestationReportContext createContextForRun(final AttestationRun run) {
        return buildContext(type -> attestationDao.findByAttestationTypeAndAttestationRun(type, run));
    }

    /**
     * Unfinished attestations are included - verifiedAt is only set once every user in the attestation is decided on,
     * so leaving them out hid the decisions already made in an ongoing attestation.
     */
    public AttestationReportContext createContext(final LocalDate from) {
        return buildContext(type -> attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqual(type, from));
    }

    private AttestationReportContext buildContext(final Function<Attestation.AttestationType, List<Attestation>> lookup) {
        final List<Attestation> itSystemUserAttestations = lookup.apply(Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
        final List<Attestation> itSystemRolesAttestations = lookup.apply(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
        // An org unit with a manager delegate gets a delegated attestation instead of an organisation one
        final List<Attestation> organisationRolesAttestations = new ArrayList<>(
                lookup.apply(Attestation.AttestationType.ORGANISATION_ATTESTATION));
        organisationRolesAttestations.addAll(lookup.apply(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION));
        return AttestationReportContext.builder()
                .itSystemUserAttestations(itSystemUserAttestations)
                .itSystemRolesAttestations(itSystemRolesAttestations)
                .organisationRolesAttestations(organisationRolesAttestations)
                .build();
    }


}
