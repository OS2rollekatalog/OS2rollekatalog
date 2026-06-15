package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemOrganisationAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.dto.ExceptedUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAssignmentOrgUnitDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAssignmentUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAssignmentUserRoleDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AssignedThroughAttestation;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.BaseUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.FunctionDao;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.hasAllOuAttestationsBeenPerformed;
import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.hasAllUserAttestationsBeenPerformed;
import static dk.digitalidentity.rc.attestation.service.util.AttestationValidation.validateAttestationOfItSystemOuIsNotPerformed;
import static dk.digitalidentity.rc.attestation.service.util.AttestationValidation.validateAttestationOfItSystemUserIsNotPerformed;
import static dk.digitalidentity.rc.util.StreamExtensions.distinctByKey;


@Slf4j
@Component
public class ItSystemUsersAttestationService {
    @Autowired
    private AttestationUserRoleAssignmentDao attestationUserRoleAssignmentDao;

    @Autowired
    private AttestationOuAssignmentsDao attestationOuAssignmentsDao;

    @Autowired
    private ItSystemUserAttestationEntryDao itSystemUserAttestationEntryDao;

    @Autowired
    private ItSystemOrganisationAttestationEntryDao itSystemOrganisationAttestationEntryDao;

	@Autowired
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

    @Autowired
    private AttestationDao attestationDao;

    @Autowired
    private AttestationCachedUserService attestationUserService;

    @Autowired
    private ItSystemService itSystemService;

    @Autowired
    private UserService userService;

    @Autowired
    private AttestationEmailNotificationService notificationService;

    @Autowired
    private TitleDao titleDao;

	@Autowired
	private FunctionDao functionDao;

    @Transactional
    public void finishOutstandingAttestations() {
        // Only consider attestations that are less than a month old
        final LocalDate since = LocalDate.now().minusMonths(1);
        attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, since).stream()
                .filter(a -> a.getVerifiedAt() == null)
                .filter(a -> isItSystemUserAttestationDone(a))
                .forEach(a -> {
                    a.setVerifiedAt(ZonedDateTime.now());
                    log.warn("Attestation finished but not verified, id: {}", a.getId());
                });
    }


    @Transactional
    public List<ItSystemRoleAttestationDTO> listItSystemUsersForAttestation(final AttestationRun run, final String userUuid) {
		// Batch-load all collections referenced by this run to avoid N+1 queries.
		List<Long> allCollectionIds = run.getAttestations().stream()
			.filter(a -> a.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
			.map(Attestation::getResponsibleCollectionId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		Map<Long, AttestationResponsibleCollection> collectionsById = attestationResponsibleCollectionDao.findAllById(allCollectionIds)
			.stream()
			.collect(java.util.stream.Collectors.toMap(AttestationResponsibleCollection::getId, c -> c));

		List<Long> collectionIds = collectionsById.values().stream()
			.filter(c -> c.getUsersUuid().contains(userUuid))
			.map(AttestationResponsibleCollection::getId)
			.toList();

		return run.getAttestations().stream()
			.filter(a -> a.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
			.filter(a -> a.getResponsibleCollectionId() != null && collectionIds.contains(a.getResponsibleCollectionId()))
                .map(attestation -> {
                    final List<AttestationUserRoleAssignment> userRoleAssignments = attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(attestation.getCreatedAt(), collectionIds);
                    final List<AttestationOuRoleAssignment> ouRoleAssignments = attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(attestation.getCreatedAt(), collectionIds);
					final String performedBy = AttestationOverviewService.resolvePerformedBy(attestation.getItSystemUserAttestationEntries(), userService);
					return ItSystemRoleAttestationDTO.builder()
                            .createdAt(attestation.getCreatedAt())
                            .itSystemId(attestation.getItSystemId())
                            .itSystemName(attestation.getItSystemName())
                            .attestationUuid(attestation.getUuid())
                            .deadline(run.getDeadline())
                            .verifiedAt(attestation.getVerifiedAt() != null ? attestation.getVerifiedAt().toLocalDate() : null)
                            .performedBy(performedBy)
                            .users(buildUserAttestations(attestation, userRoleAssignments, false))
                            .orgUnits(buildOrgUnitAttestations(attestation, ouRoleAssignments))
                            .build();
                })
                .sorted(Comparator.comparing(ItSystemRoleAttestationDTO::getDeadline).reversed())
                .filter(distinctByKey(ItSystemRoleAttestationDTO::getItSystemId))
                .toList();
    }

    @Transactional
    public ItSystemRoleAttestationDTO getAttestation(final long itSystemId, final boolean undecidedUsersOnly, String currentUserUuid) {
        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, itSystemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation not found"));
        return getAttestation(attestation, undecidedUsersOnly);
    }

    @Transactional
    public ItSystemRoleAttestationDTO getAttestation(final Attestation attestation, final boolean undecidedUsersOnly) {
		Long responsibleCollectionId = attestation.getResponsibleCollectionId();
		final List<AttestationUserRoleAssignment> userRoleAssignments = attestationUserRoleAssignmentDao
                .listValidAssignmentsByResponsibleCollectionIdAndItSystemId(attestation.getCreatedAt(), responsibleCollectionId, attestation.getItSystemId());
        final List<AttestationOuRoleAssignment> ouRoleAssignments = attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(attestation.getCreatedAt(), List.of(attestation.getResponsibleCollectionId()));
        return ItSystemRoleAttestationDTO.builder()
                .createdAt(attestation.getCreatedAt())
                .deadline(attestation.getDeadline())
                .verifiedAt(attestation.getVerifiedAt() != null ? attestation.getVerifiedAt().toLocalDate() : null)
                .itSystemId(attestation.getItSystemId())
                .itSystemName(attestation.getItSystemName())
                .users(buildUserAttestations(attestation, userRoleAssignments, undecidedUsersOnly))
                .orgUnits(buildOrgUnitAttestations(attestation, ouRoleAssignments))
                .build();
    }

    @Transactional
    public List<RoleAssignmentDTO> getUserRoleAssignments(final String attestationUuid, final String userUuid) {
        final LocalDate when = LocalDate.now();
		final Long responsibleCollection = Optional.ofNullable(attestationDao.findByUuid(attestationUuid))
			.map(Attestation::getResponsibleCollectionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation not found"));
		List<AttestationUserRoleAssignment> userRoleAssignments = attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(when, List.of(responsibleCollection));
        return userRoleAssignments.stream()
                .filter(r -> r.getUserUuid().equals(userUuid))
                .map(r -> RoleAssignmentDTO.builder()
                        .roleType(r.getRoleGroupId() != null ? RoleType.ROLEGROUP : RoleType.USERROLE)
                        .roleId(r.getRoleGroupId() != null ?  r.getRoleGroupId() : r.getUserRoleId())
                        .roleName(r.getRoleGroupId() != null ? r.getRoleGroupName() : r.getUserRoleName())
                        .itSystemName(r.getItSystemName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void verifyUser(final long itSystemId, final String userUuid, final String performedByUserId) {
        final Attestation attestation = findAttestation(itSystemId, performedByUserId);
        validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid);

        final User user = userService.getByUserId(performedByUserId);

        final ItSystemUserAttestationEntry entry = itSystemUserAttestationEntryDao.save(
                ItSystemUserAttestationEntry.builder()
                        .attestation(attestation)
                        .userUuid(userUuid)
                        .performedByUserId(performedByUserId)
                        .performedByUserUuid(user.getUuid())
                        .createdAt(ZonedDateTime.now())
                        .build());
        attestation.getItSystemUserAttestationEntries().add(entry);
        if (isItSystemUserAttestationDone(attestation)) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
    }

    @Transactional
    public void rejectUser(final long itSystemId, final String userUuid, final String remarks, final List<RoleAssignmentDTO> notApproved, final String performedByUserId) {
        final Attestation attestation = findAttestation(itSystemId, performedByUserId);
        validateAttestationOfItSystemUserIsNotPerformed(attestation, userUuid);

        final Set<String> notApprovedUserRoles = getNotApprovedUserRoles(notApproved);
        final Set<String> notApprovedRoleGroups = getNotApprovedRoleGoups(notApproved);
        final User performingUser = userService.getByUserId(performedByUserId);
        final ItSystemUserAttestationEntry entry = itSystemUserAttestationEntryDao.save(
                ItSystemUserAttestationEntry.builder()
                        .attestation(attestation)
                        .userUuid(userUuid)
                        .performedByUserId(performedByUserId)
                        .performedByUserUuid(performingUser.getUuid())
                        .createdAt(ZonedDateTime.now())
                        .remarks(remarks)
                        .rejectedRoleGroupIds(notApprovedRoleGroups)
                        .rejectedUserRoleIds(notApprovedUserRoles)
                        .build());
        attestation.getItSystemUserAttestationEntries().add(entry);
        if (isItSystemUserAttestationDone(attestation)) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
        final User user = userService.getByUuid(userUuid);
        if (user != null) {
            notificationService.sendRequestForChangeMail(userNameAndID(performingUser), userNameAndID(user), remarks, notApproved);
        }
    }

    private Set<String> getNotApprovedRoleGoups(List<RoleAssignmentDTO> notApproved) {
        Set<String> result = new HashSet<>();
        if (notApproved != null) {
            for (RoleAssignmentDTO roleAssignmentDTO : notApproved) {
                if (roleAssignmentDTO.getRoleType().equals(RoleType.ROLEGROUP)) {
                    result.add(roleAssignmentDTO.getRoleId() + "");
                }
            }
        }
        return result;
    }

    private Set<String> getNotApprovedUserRoles(List<RoleAssignmentDTO> notApproved) {
        Set<String> result = new HashSet<>();
        if (notApproved != null) {
            for (RoleAssignmentDTO roleAssignmentDTO : notApproved) {
                if (roleAssignmentDTO.getRoleType().equals(RoleType.USERROLE)) {
                    result.add(roleAssignmentDTO.getRoleId() + "");
                }
            }
        }
        return result;
    }

    @Transactional
    public void verifyOu(final long itSystemId, final String ouUuid, final String performedByUserId) {
        final Attestation attestation = findAttestation(itSystemId, performedByUserId);
        validateAttestationOfItSystemOuIsNotPerformed(attestation, ouUuid);

        final User user = userService.getByUserId(performedByUserId);
        final ItSystemOrganisationAttestationEntry entry = itSystemOrganisationAttestationEntryDao.save(
                ItSystemOrganisationAttestationEntry.builder()
                        .attestation(attestation)
                        .organisationUuid(ouUuid)
                        .performedByUserId(performedByUserId)
                        .performedByUserUuid(user.getUuid())
                        .createdAt(ZonedDateTime.now())
                        .build());
        attestation.getItSystemOrganisationAttestationEntries().add(entry);
        if (isItSystemUserAttestationDone(attestation)) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
    }


    @Transactional
    public void rejectOu(final long itSystemId, final String ouUuid, final String remarks, final String performedByUserId) {
        final Attestation attestation = findAttestation(itSystemId, performedByUserId);
        validateAttestationOfItSystemUserIsNotPerformed(attestation, ouUuid);

        final User performingUser = userService.getByUserId(performedByUserId);
        final ItSystemOrganisationAttestationEntry entry = itSystemOrganisationAttestationEntryDao.save(
                ItSystemOrganisationAttestationEntry.builder()
                        .attestation(attestation)
                        .organisationUuid(ouUuid)
                        .performedByUserId(performedByUserId)
                        .performedByUserUuid(performingUser.getUuid())
                        .createdAt(ZonedDateTime.now())
                        .remarks(remarks)
                        .build());
        attestation.getItSystemOrganisationAttestationEntries().add(entry);
        if (isItSystemUserAttestationDone(attestation)) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
		attestationDao.save(attestation);
        final User user = userService.getByUuid(performedByUserId);
        if (user != null) {
            notificationService.sendRequestForChangeMail(userNameAndID(performingUser), userNameAndID(user), remarks, Collections.emptyList());
        }
    }

	@Transactional
	public void rejectOuAndDeleteRelated(long itSystemId, String orgUnitUuid, String remarks, String userId) {
		rejectOu(itSystemId, orgUnitUuid, remarks, userId);
		deleteRelatedAttestations(itSystemId, userId);
	}

	@Transactional
	public void verifyOuAndDeleteRelated(long itSystemId, String orgUnitUuid, String userId) {
		verifyOu(itSystemId, orgUnitUuid, userId);
		deleteRelatedAttestations(itSystemId, userId);
	}

    private boolean isItSystemUserAttestationDone(final Attestation attestation) {
        final List<AttestationUserRoleAssignment> assignments =
                attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(attestation.getCreatedAt(), Collections.singletonList(attestation.getResponsibleCollectionId())).stream()
                        .filter(a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT)
                        .collect(Collectors.toList());
        final List<AttestationOuRoleAssignment> ouRoleAssignments = attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(attestation.getCreatedAt(),
			Collections.singletonList(attestation.getResponsibleCollectionId()));

        return hasAllUserAttestationsBeenPerformed(attestation, assignments, p -> Objects.equals(p.getItSystemId(), attestation.getItSystemId())) &&
                hasAllOuAttestationsBeenPerformed(attestation, ouRoleAssignments, p -> Objects.equals(p.getItSystemId(), attestation.getItSystemId()));
    }

    private Attestation findAttestation(long itSystemId, String performedByUserId) {
        final ItSystem itSystem = itSystemService.getById(itSystemId);
        if (itSystem == null || !itSystemService.getAttestationResponsibleUserIds(itSystem).contains(performedByUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong permissions.");
        }
        // Resolve the performing user's uuid so we fetch THEIR per-responsible attestation row,
        // not "the first attestation for this system" (which could belong to a different responsible).
        final User performingUser = userService.getByUserId(performedByUserId);
        if (performingUser == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong permissions.");
        }
		final Long collectionId = attestationResponsibleCollectionDao.findFirstByItSystemId((long) itSystemId)
			.map(AttestationResponsibleCollection::getId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation not found"));
        return attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
                        Attestation.AttestationType.IT_SYSTEM_ATTESTATION, itSystemId, collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation not found"));
    }

	private List<Attestation> findRelatedAttestations(long itSystemId, String performedByUserId) {
		final ItSystem itSystem = itSystemService.getById(itSystemId);
		if (itSystem == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wrong permissions.");
		}
		final Long collectionId = attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)
			.map(AttestationResponsibleCollection::getId)
			.orElse(null);
		if (collectionId == null) {
			return List.of();
		}
		return attestationDao.findByAttestationTypeAndItSystemIdAndResponsibleCollectionIdNot(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, itSystemId, collectionId);
	}

    private List<ItSystemRoleAssignmentUserDTO> buildUserAttestations(final Attestation attestation,
                                                                      final List<AttestationUserRoleAssignment> assignments,
                                                                      final boolean undecidedUsersOnly) {
        final List<AttestationUserRoleAssignment> relevantAssignments = assignments.stream()
                .filter(a -> a.getItSystemId().equals(attestation.getItSystemId()))
                .toList();
        return relevantAssignments.stream()
                // Assigned through OUs do not need to show up, since the OU assignment will be attestated
                .filter(a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT)
                .filter(distinctByKey(AttestationUserRoleAssignment::getUserUuid))
                .filter(a -> !undecidedUsersOnly || !hasUserDecisionBeenMade(attestation, a.getUserUuid()))
                .map(r -> {
                    final Optional<ItSystemUserAttestationEntry> attestationEntry = attestation.getItSystemUserAttestationEntries().stream()
                            .filter(a -> a.getUserUuid().equals(r.getUserUuid()))
                            .findFirst();
                    return ItSystemRoleAssignmentUserDTO.builder()
                            .userId(r.getUserId())
                            .userUuid(r.getUserUuid())
                            .userName(r.getUserName())
                            .verifiedByUserId(attestationEntry.map(BaseUserAttestationEntry::getPerformedByUserUuid).orElse(null))
                            .remarks(attestationEntry.map(BaseUserAttestationEntry::getRemarks).orElse(null))
                            .position(attestationUserService.getAllUserPositionsCached(r.getUserUuid()))
                            .userRoles(relevantAssignments.stream()
                                    .filter(a -> a.getUserUuid().equals(r.getUserUuid()))
                                    .map(a -> ItSystemRoleAssignmentUserRoleDTO.builder()
                                            .roleName(a.getUserRoleName())
                                            .roleId(a.getUserRoleId())
                                            .roleDescription(a.getUserRoleDescription())
                                            .assignedThroughName(a.getAssignedThroughName())
                                            .assignedThrough(a.getAssignedThroughType() != null
                                                    ? AssignedThroughAttestation.valueOf(a.getAssignedThroughType().name())
                                                    : null)
											.postponedConstraints(a.getPostponedConstraints())
                                            .build())
                                    .collect(Collectors.toList())
                            )
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ItSystemRoleAssignmentOrgUnitDTO> buildOrgUnitAttestations(Attestation attestation,
                                                                            final List<AttestationOuRoleAssignment> assignments) {
        final List<AttestationOuRoleAssignment> relevantAssignments = assignments.stream()
                .filter(a -> a.getItSystemId().equals(attestation.getItSystemId()))
                .toList();
        return relevantAssignments.stream()
                .filter(distinctByKey(AttestationOuRoleAssignment::getOuUuid))
                .filter(a -> !hasOuDecisionBeenMade(attestation, a.getOuUuid()))
                .map(r -> {
                    List<OrgUnitUserRoleAssignmentDTO> ouUserRoles = relevantAssignments.stream()
                            .filter(a -> a.getOuUuid().equals(r.getOuUuid()))
                            .map(a -> OrgUnitUserRoleAssignmentDTO.builder()
                                    .roleName(a.getRoleName())
                                    .roleId(a.getRoleId())
                                    .roleDescription(a.getRoleDescription())
                                    .exceptedUsers(a.getExceptedUserUuids().stream()
                                            .map(uuid -> userService.getByUuid(uuid))
                                            .filter(Objects::nonNull)
                                            .map(u -> ExceptedUserDTO.builder()
                                                    .userId(u.getUserId())
                                                    .name(u.getName())
                                                    .build())
                                            .collect(Collectors.toList())
                                    )
                                    .titles(lookupTitles(a.getTitleUuids()))
                                    .inherit(a.isInherit())
									.manager(a.isManager())
									.substitutes(a.isSubstitutes())
									.functions(lookupFunctions(a.getFunctionUuids()))
                                    .build())
                            .collect(Collectors.toList());
                    return ItSystemRoleAssignmentOrgUnitDTO.builder()
                            .orgUnitUuid(r.getOuUuid())
                            .orgUnitName(r.getOuName())
                            .userRoles(ouUserRoles)
                            .build();
                })
                .collect(Collectors.toList());

    }

    private List<String> lookupTitles(final List<String> titleUuids) {
        if (titleUuids == null || titleUuids.isEmpty()) {
            return Collections.emptyList();
        }
        return titleDao.findByUuidInAndActiveTrue(new HashSet<>(titleUuids)).stream()
                .map(Title::getName)
                .collect(Collectors.toList());
    }

	private List<String> lookupFunctions(final List<String> functionUuids) {
		if (functionUuids == null || functionUuids.isEmpty()) {
			return Collections.emptyList();
		}
		return functionDao.findByUuidInAndActiveTrue(new HashSet<>(functionUuids)).stream()
				.map(Function::getName)
				.collect(Collectors.toList());
	}

    /**
     * Check if the user have already been verified/declined
     */
    private static boolean hasUserDecisionBeenMade(final Attestation attestation, final String userUuid) {
        return attestation.getItSystemUserAttestationEntries().stream()
                .anyMatch(e -> userUuid.equals(e.getUserUuid()));
    }

    /**
     * Check if the ou have already been verified/declined
     */
    private static boolean hasOuDecisionBeenMade(final Attestation attestation, final String ouUuid) {
        return attestation.getItSystemOrganisationAttestationEntries().stream()
                .anyMatch(e -> ouUuid.equals(e.getOrganisationUuid()));
    }

    private static String userNameAndID(final User user) {
        return user.getName() + "(" + user.getUserId() + ")";
    }

	@Transactional
	public void deleteRelatedAttestations(long itSystemId, String performedByUserId) {
		List<Attestation> attestations = findRelatedAttestations(itSystemId, performedByUserId);
		for (Attestation attestation : attestations) {
			attestation.getOrganisationUserAttestationEntries().clear();
			attestation.getItSystemOrganisationAttestationEntries().clear();
			attestation.getItSystemUserRoleAttestationEntries().clear();
			attestation.getItSystemUserAttestationEntries().clear();
			attestation.getUsersForAttestation().clear();
			attestation.getMails().clear();
		}
		attestationDao.deleteAll(attestations);
	}
}
