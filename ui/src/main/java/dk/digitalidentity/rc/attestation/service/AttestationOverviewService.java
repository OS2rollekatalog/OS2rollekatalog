package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitRoleGroupAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttestationOverviewService {
    @Autowired
    private OrgUnitService orgUnitService;

    public List<AttestationOverviewDTO> buildOrgUnitsOverviews(final List<OrganisationAttestationDTO> orgsForAttestation, boolean readOnly) {
        return orgsForAttestation.stream()
                .map(o -> buildOrgUnitOverview(o, readOnly))
                .collect(Collectors.toList());
    }

    public static AttestationOverviewDTO buildItSystemUsersOverview(final ItSystemRoleAttestationDTO itsDto, boolean readOnly) {
        final LocalDate now = LocalDate.now();
        long total = itsDto.getUsers().size();
        long verified = itsDto.getUsers().stream()
                .filter(u -> u.getVerifiedByUserId() != null || u.getRemarks() != null)
                .count();
        long totalOrgUnit = itsDto.getOrgUnits().size();
        long verifiedOrgUnit = itsDto.getOrgUnits().stream()
                .filter(o -> o.getVerifiedByUserId() != null || o.getRemarks() != null)
                .count();
        long orgsLeft = totalOrgUnit - verifiedOrgUnit;
        return new AttestationOverviewDTO(itsDto.getCreatedAt(), readOnly, itsDto.getItSystemName(), String.valueOf(itsDto.getItSystemId()), verified,
                total - verified, total, itsDto.getDeadline(), itsDto.getDeadline().isBefore(now), null, verifiedOrgUnit, orgsLeft, totalOrgUnit);
    }

    public static List<AttestationOverviewDTO> buildItSystemsUsersOverviews(final List<ItSystemRoleAttestationDTO> itSystemUsersAttestation, boolean readOnly) {
        LocalDate now = LocalDate.now();
        return itSystemUsersAttestation.stream()
                .map(its -> buildItSystemUsersOverview(its, readOnly))
                .collect(Collectors.toList());
    }

    public static List<AttestationOverviewDTO> buildItSystemsOverviews(final List<ItSystemAttestationDTO> systemAttestationList, boolean readOnly) {
        return systemAttestationList.stream()
                .map(s -> buildItSystemOverview(s, readOnly))
                .collect(Collectors.toList());
    }

    public static AttestationOverviewDTO buildItSystemOverview(ItSystemAttestationDTO itSystemAttestation, boolean readOnly) {
        long verified = itSystemAttestation.getUserRoles().stream()
                .filter(r -> r.getVerifiedByUserId() != null || r.getRemarks() != null)
                .count();
        return new AttestationOverviewDTO(itSystemAttestation.getCreatedAt(), readOnly, itSystemAttestation.getItSystemName(), String.valueOf(itSystemAttestation.getItSystemId()),
                verified,  itSystemAttestation.getUserRoles().size() - verified,
                itSystemAttestation.getUserRoles().size(), itSystemAttestation.getDeadLine(),
                itSystemAttestation.getDeadLine().isBefore(LocalDate.now()), null, 0, 0, 0);
    }

    public AttestationOverviewDTO buildOrgUnitOverview(final OrganisationAttestationDTO organisationAttestationDto, boolean readOnly) {
        long total = organisationAttestationDto.getUserAttestations().size();
        long verified = organisationAttestationDto.getUserAttestations().stream()
                .filter(u -> u.getVerifiedByUserId() != null || u.getRemarks() != null || u.isAdRemoval())
                .count();

        List<String> substitutes = new ArrayList<>();
        OrgUnit orgUnit = orgUnitService.getByUuid(organisationAttestationDto.getOuUuid());
        if (orgUnit != null && orgUnit.getManager() != null) {
            substitutes.addAll(orgUnit.getManager().getManagerSubstitutes().stream().filter(s -> s.getOrgUnit() == null || s.getOrgUnit().getUuid().equals(organisationAttestationDto.getOuUuid())).map(s -> s.getSubstitute().getName()).collect(Collectors.toList()));
        }
        final List<OrgUnitUserRoleAssignmentItSystemDTO> orgRoleAssignments = organisationAttestationDto.getOrgUnitUserRoleAssignmentsPrItSystem();
        final List<OrgUnitRoleGroupAssignmentDTO> orgGroupAssignments = organisationAttestationDto.getOrgUnitRoleGroupAssignments();
        boolean hasOrgAssignments = !((orgRoleAssignments == null || orgRoleAssignments.isEmpty()) && (orgGroupAssignments == null || orgGroupAssignments.isEmpty()));
        int orgsAttestated = (hasOrgAssignments && organisationAttestationDto.isOrgUnitRolesVerified()) ? 1 : 0;
        int orgsToAttestate = (hasOrgAssignments && !organisationAttestationDto.isOrgUnitRolesVerified()) ? 1 : 0;

        LocalDate now = LocalDate.now();
        return new AttestationOverviewDTO(organisationAttestationDto.getCreatedAt(), readOnly, organisationAttestationDto.getOuName(), organisationAttestationDto.getOuUuid(),
                verified, total-verified, total, organisationAttestationDto.getDeadLine(), organisationAttestationDto.getDeadLine().isBefore(now),
                substitutes, orgsAttestated, orgsToAttestate, hasOrgAssignments ? 1 : 0);
    }

}
