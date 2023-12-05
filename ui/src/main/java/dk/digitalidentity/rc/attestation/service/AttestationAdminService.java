package dk.digitalidentity.rc.attestation.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;

@Service
public class AttestationAdminService {

    @Autowired
    private AttestationDao attestationDao;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private ItSystemService itSystemService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<Attestation> findAllCurrentOrganisationAttestations() {
        entityManager.setFlushMode(FlushModeType.COMMIT);
        return orgUnitService.getAllCached().stream()
                .map(this::findAttestation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<Attestation> findAllCurrentItSystemRoleAttestations() {
        entityManager.setFlushMode(FlushModeType.COMMIT);
        return itSystemService.getAll().stream()
                .map(this::findItSystemRolesAttestation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<Attestation> findAllCurrentItSystemUserAttestations() {
        entityManager.setFlushMode(FlushModeType.COMMIT);
        return itSystemService.getAll().stream()
                .map(this::findItSystemUserAttestation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<Attestation> findItSystemRolesAttestation(final ItSystem itSystem) {
        return attestationDao.findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystem.getId());
    }
    private Optional<Attestation> findItSystemUserAttestation(final ItSystem itSystem) {
        return attestationDao.findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, itSystem.getId());
    }

    private Optional<Attestation> findAttestation(final OrgUnit ou) {
        return attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadline(Attestation.AttestationType.ORGANISATION_ATTESTATION, ou.getUuid());
    }
}
