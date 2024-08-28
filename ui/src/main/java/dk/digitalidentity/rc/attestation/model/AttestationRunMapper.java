package dk.digitalidentity.rc.attestation.model;

import dk.digitalidentity.rc.attestation.model.dto.AttestationRunDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationStatusListDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AdminAttestationStatus;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.AttestationAdminService;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.attestation.controller.mvc.AttestationViewHelpers.buildBreadcrumbs;

@Component
public class AttestationRunMapper {
    @Autowired
    private OrgUnitService orgUnitService;
    @Autowired
    private ManagerSubstituteService managerSubstituteService;
    @Autowired
    private UserService userService;
    @Autowired
    private AttestationAdminService attestationAdminService;

    public List<AttestationRunDTO> toRunDTOList(final List<AttestationRun> runs) {
        return runs.stream()
                .map(this::toRunDTO)
                .toList();
    }

    public AttestationRunDTO toRunDTO(final AttestationRun run) {
        final List<Attestation> ouAttestations = run.getAttestations().stream()
                .filter(a -> a.getAttestationType() == Attestation.AttestationType.ORGANISATION_ATTESTATION)
                .sorted(Comparator.comparing(Attestation::getResponsibleOuName))
                .toList();
        final List<Attestation> systemAttestations = run.getAttestations().stream()
                .filter(a -> a.getAttestationType() != Attestation.AttestationType.ORGANISATION_ATTESTATION)
                .sorted(Comparator.comparing(Attestation::getItSystemName))
                .toList();
        final long totalAttestations = ouAttestations.size() + systemAttestations.size();
        final long finishedAttestations = ouAttestations.stream().filter(a->a.getVerifiedAt() != null).count() +
                systemAttestations.stream().filter(a->a.getVerifiedAt() != null).count();
        return new AttestationRunDTO(
                run.getId(),
                run.getCreatedAt(),
                run.getDeadline(),
                run.isSensitive(),
                totalAttestations,
                finishedAttestations,
                run.isSuperSensitive(),
                run.isFinished(),
                toOuStatusList(ouAttestations),
                toSystemStatusList(systemAttestations, Collections.emptyList())
        );
    }

    private List<AttestationStatusListDTO> toSystemStatusList(final List<Attestation> attestations, List<Attestation> userAttestations) {
        Stream<Attestation> allAttestations = Stream.concat(attestations.stream(), userAttestations.stream());
        return allAttestations
                .map(a -> {
                    final AdminAttestationStatus status = attestationAdminService.findAttestationStatus(a);
                    Optional<User> optionalByUuid = userService.getOptionalByUuid(a.getResponsibleUserUuid());
                    return new AttestationStatusListDTO(a.getId(), a.getItSystemName(), null,
                            Collections.emptyList(), optionalByUuid.orElse(null), a.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION
                            ? "Rolleopbygning" : "Rolletildelinger", status);
                })
                .collect(Collectors.toList());
    }

    private List<AttestationStatusListDTO> toOuStatusList(final List<Attestation> attestations) {
        return attestations.stream()
                .map(a -> {
                    OrgUnit ou = orgUnitService.getByUuid(a.getResponsibleOuUuid());
                    if (ou == null) {
                        return null;
                    }
                    return new AttestationStatusListDTO(a.getId(), ou.getName(), ou.getManager(),
                            managerSubstituteService.getSubstitutesForOrgUnit(ou), null, buildBreadcrumbs(ou), attestationAdminService.findAttestationStatus(a));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
