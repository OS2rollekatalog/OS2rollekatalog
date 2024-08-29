package dk.digitalidentity.rc.attestation.service;

import static dk.digitalidentity.rc.attestation.service.AttestationOverviewService.buildItSystemOverview;
import static dk.digitalidentity.rc.attestation.service.AttestationOverviewService.buildItSystemUsersOverview;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationRunDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationUserAttestationEntryDao;

import dk.digitalidentity.rc.attestation.model.AttestationMailMapper;
import dk.digitalidentity.rc.attestation.model.dto.AdminAttestationDetailsDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AdminAttestationStatus;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;

@Service
public class AttestationAdminService {
    @Autowired
    private AttestationDao attestationDao;
    @Autowired
    private OrganisationUserAttestationEntryDao organisationUserAttestationEntryDao;
    @Autowired
    private ItSystemUserAttestationEntryDao itSystemUserAttestationEntryDao;
    @Autowired
    private ItSystemRoleAttestationEntryDao itSystemRoleAttestationEntryDao;
    @Autowired
    private AttestationRunDao attestationRunDao;
    @Autowired
    private ItSystemUserRolesAttestationService itSystemUserRolesAttestationService;
    @Autowired
    private ItSystemUsersAttestationService itSystemUsersAttestationService;
    @Autowired
    private OrganisationAttestationService organisationAttestationService;
    @Autowired
    private AttestationOverviewService attestationOverviewService;
    @Autowired
    private AttestationMailMapper mailMapper;

    public List<AttestationRun> findNewestRuns(final int limit) {
        return attestationRunDao.findLatestRuns(limit);
    }

    public Optional<Attestation> getAttestation(final Long attestationId) {
        return attestationDao.findById(attestationId);
    }

    @Transactional
    public AdminAttestationDetailsDTO findAttestationDetails(final Attestation attestation) {
        final AttestationOverviewDTO overview = switch (attestation.getAttestationType()) {
            case ORGANISATION_ATTESTATION -> attestationOverviewService.buildOrgUnitOverview(organisationAttestationService.getAttestation(attestation, "", false), true);
            case IT_SYSTEM_ROLES_ATTESTATION -> buildItSystemOverview(itSystemUserRolesAttestationService.getItSystemAttestation(attestation), true);
            case IT_SYSTEM_ATTESTATION -> buildItSystemUsersOverview(itSystemUsersAttestationService.getAttestation(attestation, false), true);
        };
        return AdminAttestationDetailsDTO.builder()
                .overview(overview)
                .attestationType(attestation.getAttestationType())
                .sentEmails(mailMapper.sentMail(attestation))
                .id(attestation.getId())
                .build();
    }

    public AdminAttestationStatus findAttestationStatus(final Attestation attestation) {
        if (attestation.getVerifiedAt() != null) {
            return AdminAttestationStatus.FINISHED;
        }
        if (attestation.getAttestationType() == Attestation.AttestationType.ORGANISATION_ATTESTATION) {
            if (organisationUserAttestationEntryDao.countByAttestationId(attestation.getId()) > 0) {
                return AdminAttestationStatus.ON_GOING;
            }
        } else if (attestation.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ATTESTATION) {

            if (itSystemUserAttestationEntryDao.countByAttestationId(attestation.getId()) > 0) {
                return AdminAttestationStatus.ON_GOING;
            }
        } else if (attestation.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION) {
            if (itSystemRoleAttestationEntryDao.countByAttestationId(attestation.getId()) > 0) {
                return AdminAttestationStatus.ON_GOING;
            }
        }
        return AdminAttestationStatus.NOT_STARTED;
    }

}
