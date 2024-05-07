package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationSystemRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.attestation.model.Mapper.toItSystemAttestationDto;
import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.hasAllRoleAssignmentAttestationsBeenPerformed;
import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.hasRoleAssignmentAttestationBeenPerformed;
import static dk.digitalidentity.rc.attestation.service.util.AttestationValidation.validateAttestationOfItSystemUserRoleIsNotPerformed;

@Slf4j
@Component
public class ItSystemUserRolesAttestationService {
    @Autowired
    private AttestationSystemRoleAssignmentDao attestationSystemRoleAssignmentDAO;
    @Autowired
    private AttestationDao attestationDao;
    @Autowired
    private ItSystemRoleAttestationEntryDao itSystemRoleAttestationEntryDao;
    @Autowired
    private UserService userService;
    @Autowired
    private ItSystemService itSystemService;
    @Autowired
    private AttestationEmailNotificationService notificationService;
    @Autowired
    private UserRoleService userRoleService;


    @Transactional
    public void finishOutstandingAttestations() {
        // Only consider attestations that are less than a month old
        final LocalDate since = LocalDate.now().minusMonths(1);
        attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, since).stream()
                .filter(a -> a.getVerifiedAt() == null)
                .filter(a -> isItSystemUserRoleAttestationDone(a.getCreatedAt(), a))
                .forEach(a -> {
                    a.setVerifiedAt(ZonedDateTime.now());
                    log.warn("Attestation finished but not verified, id: {}", a.getId());
                });
    }

    @Transactional
    public List<ItSystemAttestationDTO> listAllItSystemsForAttestation(final LocalDate when) {
        return attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, when).stream()
            .map(attestation -> {
                    try (final Stream<AttestationSystemRoleAssignment> systemRoleAssignments = attestationSystemRoleAssignmentDAO
                            .streamValidAttestationsByResponsibleUser(when, attestation.getResponsibleUserUuid())) {
                        return toItSystemAttestationDto(attestation, systemRoleAssignments.toList());
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ItSystemAttestationDTO> listItSystemsForAttestation(final LocalDate when, String userUuid) {
        final List<Attestation> attestations = attestationDao.findByAttestationTypeAndResponsibleUserUuidAndVerifiedAtIsNull(
                Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, userUuid);
        try (final Stream<AttestationSystemRoleAssignment> systemRoleAssignments = attestationSystemRoleAssignmentDAO.streamValidAttestationsByResponsibleUser(when, userUuid)) {
            final List<AttestationSystemRoleAssignment> systemRoleAssignmentsList = systemRoleAssignments.toList();
            return attestations.stream()
                    .map(system -> toItSystemAttestationDto(system, systemRoleAssignmentsList))
                    .collect(Collectors.toList());
        }
    }


    @Transactional
    public ItSystemAttestationDTO getAttestation(final long itSystemId, final boolean undecidedUserRolesOnly) {
        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(
                Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation not found"));
        try (Stream<AttestationSystemRoleAssignment> validAttestationsByItSystemId = attestationSystemRoleAssignmentDAO.streamValidAttestationsByItSystemId(LocalDate.now(), itSystemId)) {
            final List<AttestationSystemRoleAssignment> systemRoleAssignments = validAttestationsByItSystemId
                    .filter(a -> !undecidedUserRolesOnly || !hasRoleAssignmentAttestationBeenPerformed(attestation, a))
                    .toList();
            return toItSystemAttestationDto(attestation, systemRoleAssignments);
        }
    }

    @Transactional
    public void verifyUserRole(final long itSystemId, final long userRoleId, final String performedByUserId) {
        final LocalDate when = LocalDate.now();
        final Attestation attestation = findAttestation(itSystemId, performedByUserId);
        validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, userRoleId);

        final User user = userService.getByUserId(performedByUserId);
        final ItSystemRoleAttestationEntry attachedUserRoleAttestation = itSystemRoleAttestationEntryDao.save(ItSystemRoleAttestationEntry.builder()
                .attestation(attestation)
                .userRoleId(userRoleId)
                .performedByUserUuid(user.getUuid())
                .performedByUserId(performedByUserId)
                .createdAt(ZonedDateTime.now())
                .remarks(null)
                .build());
        attestation.getItSystemUserRoleAttestationEntries().add(attachedUserRoleAttestation);
        if (isItSystemUserRoleAttestationDone(when, attestation)) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
    }

    @Transactional
    public void rejectUserRole(final long itSystemId, final long userRoleId, final String performedByUserId, final String remarks) {
        final LocalDate when = LocalDate.now();
        final Attestation attestation = findAttestation(itSystemId, performedByUserId);
        validateAttestationOfItSystemUserRoleIsNotPerformed(attestation, userRoleId);

        final User performingUser = userService.getByUserId(performedByUserId);
        final ItSystemRoleAttestationEntry attachedUserRoleAttestation = itSystemRoleAttestationEntryDao.save(ItSystemRoleAttestationEntry.builder()
                .attestation(attestation)
                .userRoleId(userRoleId)
                .performedByUserUuid(performingUser.getUuid())
                .performedByUserId(performedByUserId)
                .createdAt(ZonedDateTime.now())
                .remarks(remarks)
                .build());
        attestation.getItSystemUserRoleAttestationEntries().add(attachedUserRoleAttestation);
        if (isItSystemUserRoleAttestationDone(when, attestation)) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }

        ItSystem itSystem = itSystemService.getById(itSystemId);
        if (itSystem != null) {
            // If the it-system could not be found then we can assume the role change request doesn't matter
            Optional<UserRole> userRole = userRoleService.getByItSystem(itSystem).stream()
                    .filter(r -> r.getId() == userRoleId)
                    .findFirst();
            userRole.ifPresent(role -> notificationService.sendRequestForRoleChange(userNameAndID(performingUser), role.getName(), itSystem.getName()));
        }
    }

    private Attestation findAttestation(long itSystemId, final String performedByUserId) {
        final ItSystem itSystem = itSystemService.getById(itSystemId);
        if (itSystem == null || itSystem.getAttestationResponsible() == null || !itSystem.getAttestationResponsible().getUserId().equals(performedByUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong permissions.");
        }

        return attestationDao.findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(
                        Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation not found"));
    }

    private boolean isItSystemUserRoleAttestationDone(final LocalDate when, final Attestation attestation) {
        try (final Stream<AttestationSystemRoleAssignment> attestations = attestationSystemRoleAssignmentDAO.streamValidAttestationsByResponsibleUser(when, attestation.getResponsibleUserUuid())) {
            if (hasAllRoleAssignmentAttestationsBeenPerformed(attestation, attestations, p -> p.getItSystemId() == attestation.getItSystemId())) {
                return true;
            }
        }
        return false;
    }

    private static String userNameAndID(final User user) {
        return user.getName() + "(" + user.getUserId() + ")";
    }
}
