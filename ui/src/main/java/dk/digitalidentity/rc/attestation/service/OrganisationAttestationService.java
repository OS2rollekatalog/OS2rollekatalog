package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.dto.ExceptedUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitRoleGroupAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentSinceLastAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleGroupDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserRoleDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserRoleItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AssignedThroughAttestation;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleType;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.BaseUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.attestation.model.Mapper.userRoleGroups;
import static dk.digitalidentity.rc.util.StreamExtensions.distinctByKey;

@Component
@Slf4j

public class OrganisationAttestationService {
    @Autowired
    private AttestationUserRoleAssignmentDao userRoleAssignmentDao;
    @Autowired
    private AttestationOuAssignmentsDao ouAssignmentsDao;
    @Autowired
    private AttestationDao attestationDao;
    @Autowired
    private OrganisationUserAttestationEntryDao organisationUserAttestationEntryDao;
    @Autowired
    private OrganisationRoleAttestationEntryDao organisationRoleAttestationEntryDao;
    @Autowired
    private ManagerSubstituteService managerSubstituteService;
    @Autowired
    private UserService userService;
    @Autowired
    private AttestationCachedUserService attestationUserService;
    @Autowired
    private OrgUnitService orgUnitService;
    @Autowired
    private AttestationEmailNotificationService emailNotificationService;
    @Autowired
    private TitleDao titleDao;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private OrganisationAttestationService self;

    @Transactional
    public List<OrganisationAttestationDTO> listAllOrganisationsForAttestation(final LocalDate when) {
        return attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(Attestation.AttestationType.ORGANISATION_ATTESTATION, when).stream()
            .map(a -> toShallowOrganisationDto(when, a))
                .collect(Collectors.toList());
    }

    public List<OrganisationAttestationDTO> listOrganisationsForAttestation(final LocalDate when, final User currentUser, final List<User> substituteForUsers) {
        entityManager.setFlushMode(FlushModeType.COMMIT);
        return Stream.concat(Stream.of(currentUser), substituteForUsers.stream())
                .flatMap(u -> self.listOrganisationsForAttestation(when, currentUser, u.getUuid()))
                .filter(distinctByKey(OrganisationAttestationDTO::getAttestationUuid))
                .collect(Collectors.toList());
    }

    @Transactional
    public Stream<OrganisationAttestationDTO> listOrganisationsForAttestation(final LocalDate when, final User currentUser, final String userUuid) {
        final User user = userService.getByUuid(userUuid);
        final List<OrgUnit> orgUnitsForUser = orgUnitService.getByManagerMatchingUser(user);
        return orgUnitsForUser.stream()
                .filter(ou -> managerSubstituteService.isManagerForOrgUnit(currentUser, ou) || managerSubstituteService.isSubstituteforOrgUnit(currentUser, ou))
                .map(ou -> attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadline(Attestation.AttestationType.ORGANISATION_ATTESTATION, ou.getUuid())
                        .map(u -> toShallowOrganisationDto(when, u)).orElse(null))
                .filter(Objects::nonNull);
    }

    @Transactional
    public OrganisationAttestationDTO getAttestation(final String orgUnitUuid, final String currentUserUuid, final boolean undecidedUsersOnly) {
        final LocalDate when = LocalDate.now();

        final List<AttestationUserRoleAssignment> userAssignments = userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(when, orgUnitUuid);
        final List<AttestationOuRoleAssignment> organisationAssignments = ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(when, orgUnitUuid);
        final var ouName = userAssignments.stream().filter(r -> r.getResponsibleOuName() != null)
                .findFirst().map(AttestationUserRoleAssignment::getResponsibleOuName).orElse("");

        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        if (attestation == null) {
            return null;
        }
        // Extract all roles that have been valid in between last attestation and now - so the person doing the attestation know
        // which roles have been active between attestations.
        final Attestation previousAttestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidAndVerifiedAtIsNotNullOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        final LocalDate previousAttestationDate = previousAttestation != null ? previousAttestation.getVerifiedAt().toLocalDate() : when;
        final List<AttestationUserRoleAssignment> temporaryAssignmentsSinceLastAttestation = userRoleAssignmentDao.
                listAssignmentsWhichHaveBeenValidBetweenByResponsibleOu(previousAttestationDate, when, orgUnitUuid);

        return markCurrentUserReadonly(currentUserUuid,
                OrganisationAttestationDTO.builder()
                .attestationUuid(attestation.getUuid())
                .deadLine(attestation.getDeadline())
                .ouName(ouName)
                .ouUuid(orgUnitUuid)
                .orgUnitRolesVerified(isOrgVerified(attestation))
                .roleAssignmentsSinceLastAttestation(buildRoleAssignmentChanges(temporaryAssignmentsSinceLastAttestation))
                .orgUnitRoleGroupAssignments(orgUnitRoleGroups(organisationAssignments))
                .orgUnitUserRoleAssignmentsPrItSystem(orgUnitUserRolesPrItSystem(organisationAssignments))
                .userAttestations(buildUserAttestations(userAssignments, attestation, undecidedUsersOnly, when))
                .build()
        );
    }

    @Transactional
    public List<RoleAssignmentDTO> getUserRoleAssignments(final String attestationUuid, final String userUuid) {
        final LocalDate when = LocalDate.now();
        final Attestation attestation = attestationDao.findByUuid(attestationUuid);
        final List<AttestationUserRoleAssignment> userAssignments = userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(when, attestation.getResponsibleOuUuid());

        return userAssignments.stream()
                .filter(a -> a.getUserUuid().equals(userUuid))
                .filter(a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT)
                .map(a -> RoleAssignmentDTO.builder()
                        .roleType(a.getRoleGroupId() != null ? RoleType.ROLEGROUP : RoleType.USERROLE)
                        .roleName(a.getRoleGroupId() != null ? a.getRoleGroupName() : a.getUserRoleName())
                        .roleId(a.getRoleGroupId() != null ? a.getRoleGroupId() : a.getUserRoleId())
                        .build())
                .distinct()
                .collect(Collectors.toList());
    }


    @Transactional
    public void verifyUser(final String orgUnitUuid, final String userUuid, final String performedByUserId) {
        final User user = userService.getByUserId(performedByUserId);
        if (user.getUuid().equals(userUuid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User can not verify itself");
        }

        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        ensureUserEntryDoesntExist(userUuid, attestation);
        createUserEntry(attestation, userUuid, performedByUserId, null, false);
        if (isOrganisationAttestationDone(attestation, LocalDate.now())) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
    }

    @Transactional
    public void rejectUser(final String orgUnitUuid, final String userUuid, final String performedByUserId, final String remarks, final List<RoleAssignmentDTO> notApprovedRoleAssignments) {
        final User performingUser = userService.getByUserId(performedByUserId);
        if (performingUser.getUuid().equals(userUuid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User can not verify itself");
        }

        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        ensureUserEntryDoesntExist(userUuid, attestation);
        createUserEntry(attestation, userUuid, performedByUserId, remarks, false);
        if (isOrganisationAttestationDone(attestation, LocalDate.now())) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
        final User user = userService.getByUuid(userUuid);
        if (user != null) {
            emailNotificationService.sendRequestForChangeMail(
                    userNameAndID(performingUser),
                    userNameAndID(user), remarks, notApprovedRoleAssignments);
        }
    }

    @Transactional
    public void requestAdRemoval(final String orgUnitUuid, final String userUuid, final String performedByUserId) {
        final User performingUser = userService.getByUserId(performedByUserId);
        if (performingUser.getUuid().equals(userUuid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User can not verify itself");
        }
        final User user = userService.getByUuid(userUuid);

        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        ensureUserEntryDoesntExist(userUuid, attestation);
        createUserEntry(attestation, userUuid, performedByUserId, null, true);
        if (isOrganisationAttestationDone(attestation, LocalDate.now())) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
        emailNotificationService.sendRequestForAdRemoval(userNameAndID(performingUser), userNameAndID(user));
    }

    @Transactional
    public void acceptOrgUnitRoles(final String orgUnitUuid, final String performedByUserId) {
        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        ensureOrgRoleEntryDoesntExist(attestation);
        attestation.setOrganisationRolesAttestationEntry(
                organisationRoleAttestationEntryDao.save(OrganisationRoleAttestationEntry.builder()
                                .attestation(attestation)
                                .createdAt(ZonedDateTime.now())
                                .performedByUserId(performedByUserId)
                                .performedByUserUuid(userService.getByUserId(performedByUserId).getUuid())
                                .remarks(null)
                        .build()));
        if (isOrganisationAttestationDone(attestation, LocalDate.now())) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
    }

    @Transactional
    public void rejectOrgUnitRoles(final String orgUnitUuid, final String performedByUserId, final String remarks) {
        final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid);
        ensureOrgRoleEntryDoesntExist(attestation);
        attestation.setOrganisationRolesAttestationEntry(
                organisationRoleAttestationEntryDao.save(OrganisationRoleAttestationEntry.builder()
                                .attestation(attestation)
                                .createdAt(ZonedDateTime.now())
                                .performedByUserId(performedByUserId)
                                .performedByUserUuid(userService.getByUserId(performedByUserId).getUuid())
                                .remarks(remarks)
                        .build()));
        if (isOrganisationAttestationDone(attestation, LocalDate.now())) {
            attestation.setVerifiedAt(ZonedDateTime.now());
        }
    }

    private OrganisationAttestationDTO markCurrentUserReadonly(final String currentUserUuid, final OrganisationAttestationDTO organisationAttestationDto) {
        organisationAttestationDto.getUserAttestations().stream().filter(u -> u.getUserUuid().equals(currentUserUuid)).forEach(u -> u.setReadOnly(true));
        return organisationAttestationDto;
    }

    private static void ensureOrgRoleEntryDoesntExist(final Attestation attestation) {
        if (attestation.getOrganisationRolesAttestationEntry() != null
                && attestation.getOrganisationRolesAttestationEntry().getPerformedByUserId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organisation roles already verified");
        }
    }

    private boolean isOrganisationAttestationDone(final Attestation attestation, final LocalDate when) {
        if (attestation.getOrganisationRolesAttestationEntry() == null) {
            return false;
        }
        final List<String> processedUserUuid = attestation.getOrganisationUserAttestationEntries().stream()
                .map(BaseUserAttestationEntry::getUserUuid)
                .toList();
        final List<AttestationUserRoleAssignment> userAssignments = userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(when, attestation.getResponsibleOuUuid());
        return userAssignments.stream()
                .filter(distinctByKey(AttestationUserRoleAssignment::getUserUuid))
                .allMatch(a -> processedUserUuid.contains(a.getUserUuid()));
    }

    private void createUserEntry(final Attestation attestation,  final String userUuid, final String performedByUserId, final String remarks,
                                 final boolean adRemoval) {
        final OrganisationUserAttestationEntry entry =
                organisationUserAttestationEntryDao.save(
                        OrganisationUserAttestationEntry.builder()
                                .attestation(attestation)
                                .performedByUserId(performedByUserId)
                                .performedByUserUuid(userService.getByUserId(performedByUserId).getUuid())
                                .userUuid(userUuid)
                                .adRemoval(adRemoval)
                                .remarks(remarks)
                                .createdAt(ZonedDateTime.now())
                                .build());
        attestation.getOrganisationUserAttestationEntries().add(entry);
    }

    private static void ensureUserEntryDoesntExist(final String userUuid, final Attestation attestation) {
        final Optional<OrganisationUserAttestationEntry> existingEntry = attestation.getOrganisationUserAttestationEntries().stream()
                .filter(e -> e.getUserUuid().equals(userUuid))
                .findFirst();
        if (existingEntry.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already verified");
        }
    }

    /**
     * Convert to {@link OrganisationAttestationDTO} will only include user assignments, not organisation level assignments
     */
    private OrganisationAttestationDTO toShallowOrganisationDto(final LocalDate when, final Attestation attestationOrganisation) {
        final List<AttestationUserRoleAssignment> userRoleAssignments = userRoleAssignmentDao
                .listValidAssignmentsByResponsibleOu(when, attestationOrganisation.getResponsibleOuUuid());
        return OrganisationAttestationDTO.builder()
                .attestationUuid(attestationOrganisation.getUuid())
                .ouUuid(attestationOrganisation.getResponsibleOuUuid())
                .ouName(attestationOrganisation.getResponsibleOuName())
                .orgUnitRolesVerified(isOrgVerified(attestationOrganisation))
                .deadLine(attestationOrganisation.getDeadline())
                .userAttestations(buildUserAttestations(userRoleAssignments, attestationOrganisation, false, when))
                .build();
    }

    private static boolean isOrgVerified(Attestation attestationOrganisation) {
        return attestationOrganisation.getOrganisationRolesAttestationEntry() != null &&
                (attestationOrganisation.getOrganisationRolesAttestationEntry().getPerformedByUserId() != null
                        || attestationOrganisation.getOrganisationRolesAttestationEntry().getRemarks() != null);
    }

    private List<RoleAssignmentSinceLastAttestationDTO> buildRoleAssignmentChanges(final List<AttestationUserRoleAssignment> assignments) {
        return assignments.stream()
                .map(a -> RoleAssignmentSinceLastAttestationDTO.builder()
                        .assignedTo(a.getValidTo())
                        .assignedFrom(a.getValidFrom())
                        .userId(a.getUserId())
                        .userUuid(a.getUserUuid())
                        .userName(a.getUserName())
                        .assignedThrough(AssignedThroughAttestation.valueOf(a.getAssignedThroughType().name()))
                        .assignedThroughName(a.getAssignedThroughName())
                        .roleType(a.getRoleGroupId() != null ? RoleType.ROLEGROUP : RoleType.USERROLE)
                        .roleId(a.getRoleGroupId() != null ? a.getRoleGroupId() : a.getUserRoleId())
                        .roleName(a.getRoleGroupId() != null ? a.getRoleGroupName() : a.getUserRoleName())
                        .build())
                .collect(Collectors.toList());
    }

    private List<UserAttestationDTO> buildUserAttestations(final List<AttestationUserRoleAssignment> assignments, final Attestation attestation,
                                                           boolean undecidedUsersOnly, final LocalDate when) {
        final List<AttestationUserRoleAssignment> distinctAssignments = assignments.stream()
                .filter(distinctByKey(AttestationUserRoleAssignment::getUserUuid))
                .toList();
        final var userRoleGroupAssignments = assignments.stream()
                .filter(p -> p.getRoleGroupId() != null)
                .toList();
        final var userRoleAssignments = assignments.stream()
                .filter(p -> p.getRoleGroupId() == null)
                .toList();
        return distinctAssignments.stream()
                        .map(u -> {
                            final Optional<OrganisationUserAttestationEntry> entry = findUserAttestation(attestation, u);
                            // Find assignments where the it-system responsible, is responsible for attestation
                            final List<AttestationUserRoleAssignment> systemResponseAssignments = userRoleAssignmentDao
                                    .listValidAssignmentsForUserHandledByItSystemResponsible(when, u.getUserUuid());
                            // Find assignments that another department is responsible for
                            final List<AttestationUserRoleAssignment> otherDepartmentAssignments =
                                    userRoleAssignmentDao.listValidAssignmentsForUserWhereResponsibleOUIsNot(when, u.getUserUuid(), u.getRoleOuUuid());
                            // Now group all "other" roles
                            final List<AttestationUserRoleAssignment> otherRoles = Stream.concat(
                                            Stream.concat(userRoleAssignments.stream(), otherDepartmentAssignments.stream().filter(a -> a.getRoleGroupId() == null)),
                                            systemResponseAssignments.stream())
                                    .collect(Collectors.toList());
                            final List<AttestationUserRoleAssignment> otherRoleGroups = Stream.concat(userRoleGroupAssignments.stream(),
                                            otherDepartmentAssignments.stream().filter(a -> a.getRoleGroupId() != null)).toList();
                            // Inherited OU assignments and assignments where the it-system responsible is responsible should go into doNotVerify..
                            final List<UserRoleItSystemDTO> doNotVerifyUserRolesPrItSystem = userRolesPrItSystem(u.getUserUuid(), otherRoles,
                                    a -> ((a.getAssignedThroughType() == AssignedThroughType.ORGUNIT && a.isInherited()) || a.getResponsibleUserUuid() != null));
                            final List<RoleGroupDTO> doNotVerifyRoleGroups = userRoleGroups(u.getUserUuid(), otherRoleGroups,
                                    a -> a.getAssignedThroughType() == AssignedThroughType.ORGUNIT && a.isInherited());

                            String userPositionsCached = attestationUserService.getUserPositionsCached(u.getUserUuid(), u.getRoleOuUuid());
                            if (!Objects.equals(u.getRoleOuUuid(), attestation.getResponsibleOuUuid())) {
                                // Add the OU in case it differs from current
                                userPositionsCached += "(" + u.getRoleOuName() + ")";
                            }
                            return UserAttestationDTO.builder()
                                    .userName(u.getUserName())
                                    .userId(u.getUserId())
                                    .userUuid(u.getUserUuid())
                                    .position(userPositionsCached)
                                    .verifiedByUserId(entry.map(BaseUserAttestationEntry::getPerformedByUserId).orElse(null))
                                    .remarks(entry.map(BaseUserAttestationEntry::getRemarks).orElse(null))
                                    .doNotVerifyRoleGroups(doNotVerifyRoleGroups)
                                    .doNotVerifyUserRolesPrItSystem(doNotVerifyUserRolesPrItSystem)
                                    .userRolesPrItSystem(userRolesPrItSystem(u.getUserUuid(), userRoleAssignments, a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT))
                                    .roleGroups(userRoleGroups(u.getUserUuid(), userRoleGroupAssignments, a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT))
                                    .manager(u.isManager())
                                    .adRemoval(entry.map(OrganisationUserAttestationEntry::isAdRemoval).orElse(false))
                                    .build();
                        })
                .filter(u -> !undecidedUsersOnly || (u.getRemarks() == null && u.getVerifiedByUserId() == null))
                .collect(Collectors.toList());
    }

    private static Optional<OrganisationUserAttestationEntry> findUserAttestation(final Attestation attestation, final AttestationUserRoleAssignment assignment) {
        return attestation.getOrganisationUserAttestationEntries().stream()
                .filter(a -> a.getUserUuid().equals(assignment.getUserUuid()))
                .findFirst();
    }

    private List<UserRoleItSystemDTO> userRolesPrItSystem(final String userUuid, final List<AttestationUserRoleAssignment> assignments,
                                                          final Predicate<AttestationUserRoleAssignment> assignmentPredicate) {
        final var userRoleAssignments = assignments.stream()
                .filter(p -> p.getUserUuid().equals(userUuid))
                .filter(assignmentPredicate)
                .toList();
        return userRoleAssignments.stream()
                .filter(distinctByKey(AttestationUserRoleAssignment::getItSystemId))
                .map(a -> UserRoleItSystemDTO.builder()
                        .itSystemId(a.getItSystemId())
                        .itSystemName(a.getItSystemName())
                        .userRoles(userRolesFor(userRoleAssignments, a.getItSystemId()))
                        .build())
                .toList();
    }

    private List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRolesPrItSystem(final List<AttestationOuRoleAssignment> assignments) {
        final var orgRoleAssignments = assignments.stream()
                .filter(p -> p.getRoleGroupId() == null)
                .filter(p -> p.getAssignedThroughType() == AssignedThroughType.DIRECT || p.getAssignedThroughType() == AssignedThroughType.ORGUNIT)
                .toList();
        return orgRoleAssignments.stream()
                .filter(distinctByKey(AttestationOuRoleAssignment::getItSystemId))
                .map(a -> OrgUnitUserRoleAssignmentItSystemDTO.builder()
                        .itSystemId(a.getItSystemId())
                        .itSystemName(a.getItSystemName())
                        .userRoles(toOuUserAssignmentDTOs(orgRoleAssignments.stream()
                                .filter(r -> Objects.equals(r.getItSystemId(), a.getItSystemId()))
                                .collect(Collectors.toList())))
                        .build())
                .toList();
    }

    private List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroups(final List<AttestationOuRoleAssignment> assignments) {
        final var orgRoleGroupAssignments = assignments.stream()
                .filter(p -> p.getAssignedThroughType() == AssignedThroughType.DIRECT || p.getAssignedThroughType() == AssignedThroughType.ORGUNIT)
                .filter(p -> p.getRoleGroupId() != null)
                .toList();

        return orgRoleGroupAssignments.stream()
                .filter(distinctByKey(AttestationOuRoleAssignment::getRoleGroupId))
                .map(a -> {
                    final List<String> titles = a.getTitleUuids().stream()
                            .map(t -> titleDao.findById(t).map(Title::getName).orElse(t))
                            .toList();
                    return OrgUnitRoleGroupAssignmentDTO.builder()
                                    .groupDescription(a.getRoleGroupDescription())
                                    .groupName(a.getRoleGroupName())
                                    .titles(titles)
                                    .exceptedUsers(a.getExceptedUserUuids().stream()
                                            .map(u -> ExceptedUserDTO.builder()
                                                    .userId(attestationUserService.userIdFromUuidCached(u))
                                                    .name(attestationUserService.userNameFromUuidCached(u))
                                                    .build())
                                            .collect(Collectors.toList()))
                                    .groupId(a.getRoleGroupId())
                                    .userRoles(toOuUserAssignmentDTOs(orgRoleGroupAssignments, a.getRoleGroupId()))
                                    .inherit(!a.isInherited() && a.getAssignedThroughType() == AssignedThroughType.ORGUNIT)
                                    .build();
                        }
                )
                .collect(Collectors.toList());
    }

    private List<UserRoleDTO> userRolesFor(final List<AttestationUserRoleAssignment> assignments, final long itSystemId) {
        return assignments.stream()
                .filter(a -> a.getItSystemId() == itSystemId)
                .map(a -> UserRoleDTO.builder()
                        .roleId(a.getUserRoleId())
                        .roleName(a.getUserRoleName())
                        .roleDescription(a.getUserRoleDescription())
                        .itSystemName(a.getItSystemName())
                        .inherited(a.isInherited())
                        .assignedThroughName(a.getAssignedThroughName())
                        .assignedThrough(a.getAssignedThroughType() != null
                                ? AssignedThroughAttestation.valueOf(a.getAssignedThroughType().name())
                                : null)
                        .responsible(a.getResponsibleUserUuid() != null ?
                                attestationUserService.userNameFromUuidCached(a.getResponsibleUserUuid()) : null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<OrgUnitUserRoleAssignmentDTO> toOuUserAssignmentDTOs(final List<AttestationOuRoleAssignment> assignments) {
        return assignments.stream()
                .map(this::toOuAssignmentDTO)
                .collect(Collectors.toList());
    }

    private List<OrgUnitUserRoleAssignmentDTO> toOuUserAssignmentDTOs(final List<AttestationOuRoleAssignment> assignments, final long roleGroupId) {
        return assignments.stream()
                .filter(a -> roleGroupId == a.getRoleGroupId())
                .map(this::toOuAssignmentDTO)
                .collect(Collectors.toList());
    }

    private OrgUnitUserRoleAssignmentDTO toOuAssignmentDTO(final AttestationOuRoleAssignment assignment) {
        final List<String> titles = assignment.getTitleUuids().stream()
                .map(t -> titleDao.findById(t).map(Title::getName).orElse(t))
                .toList();
        return OrgUnitUserRoleAssignmentDTO.builder()
                .inherit(assignment.isInherit())
                .roleId(assignment.getRoleId())
                .exceptedUsers(assignment.getExceptedUserUuids().stream()
                        .map(u -> ExceptedUserDTO.builder()
                                .userId(attestationUserService.userIdFromUuidCached(u))
                                .name(attestationUserService.userNameFromUuidCached(u))
                                .build())
                        .collect(Collectors.toList()))
                .titles(titles)
                .roleDescription(assignment.getRoleDescription())
                .roleName(assignment.getRoleName())
                .build();
    }


    private static String userNameAndID(final User user) {
        return user.getName() + "(" + user.getUserId() + ")";
    }

}
