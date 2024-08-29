package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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

    public AttestationReportContext createContext(final LocalDate from) {
        final List<Attestation> itSystemUserAttestations = attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, from);
        final List<Attestation> itSystemRolesAttestations = attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, from);
        final List<Attestation> organisationRolesAttestations = attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(Attestation.AttestationType.ORGANISATION_ATTESTATION, from);
        return AttestationReportContext.builder()
                .itSystemUserAttestations(itSystemUserAttestations)
                .itSystemRolesAttestations(itSystemRolesAttestations)
                .organisationRolesAttestations(organisationRolesAttestations)
                .build();
    }

}
