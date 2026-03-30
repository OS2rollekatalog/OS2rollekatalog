package dk.digitalidentity.rc.attestation.service;

import static dk.digitalidentity.rc.attestation.model.AttestationRoleMapper.userRoleGroups;
import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.isSensitiveUser;
import static dk.digitalidentity.rc.util.StreamExtensions.distinctByKey;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
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
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.BaseUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.FunctionDao;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
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

	@Autowired
	private FunctionDao functionDao;

	@Autowired
	private SettingsService settingsService;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private OrganisationAttestationService self;
	@Autowired
	private ManagerDelegateAttestationService managerDelegateAttestationService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private EmailQueueService emailQueueService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	public List<OrgUnit> getAllOrgUnitsWithAttestations(LocalDate when) {
		final LocalDate since = when.minusMonths(12);
		return attestationDao.findByAttestationTypeInAndDeadlineIsGreaterThanEqual(List.of(Attestation.AttestationType.ORGANISATION_ATTESTATION, Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION), since).stream()
				.map(Attestation::getResponsibleOuUuid)
				.map(uuid -> orgUnitService.getByUuid(uuid))
				.filter(Objects::nonNull)
				.toList();
	}

	@Transactional
	public void finishOutstandingAttestations() {
		// Only consider attestations that are less than a month old
		final LocalDate since = LocalDate.now().minusMonths(1);
		attestationDao.findByAttestationTypeInAndDeadlineIsGreaterThanEqual(List.of(Attestation.AttestationType.ORGANISATION_ATTESTATION, Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION), since).stream()
				.filter(a -> a.getVerifiedAt() == null)
				.filter(a -> isOrganisationAttestationDone(a, a.getCreatedAt(), false, null))
//				.filter(a -> !settingsService.isADAttestationEnabled())
				.forEach(a -> {
					a.setVerifiedAt(ZonedDateTime.now());
					log.warn("Attestation finished but not verified, id: {}", a.getId());
				});
	}

	@Transactional
	public List<OrganisationAttestationDTO> listOrganisationsForAttestation(final AttestationRun run, final User currentUser, final List<User> substituteForUsers) {
		entityManager.setFlushMode(FlushModeType.COMMIT);
		return Stream.concat(Stream.of(currentUser), substituteForUsers.stream())
				.flatMap(u -> self.listOrganisationsForAttestation(run, currentUser, u.getUuid()))
				.sorted(Comparator.comparing(OrganisationAttestationDTO::getDeadLine).reversed())
				.filter(distinctByKey(OrganisationAttestationDTO::getAttestationUuid))
				.collect(Collectors.toList());
	}

	@Transactional
	public Stream<OrganisationAttestationDTO> listOrganisationsForAttestation(final AttestationRun run, final User currentUser, final String userUuid) {
		final List<OrgUnit> orgUnitsForUser = userService.getOptionalByUuid(userUuid)
				.map(u -> orgUnitService.getByManagerMatchingUser(u))
				.orElse(Collections.emptyList());
		return orgUnitsForUser.stream()
				.filter(ou -> managerSubstituteService.isManagerForOrgUnit(currentUser, ou) || managerSubstituteService.isSubstituteforOrgUnit(currentUser, ou))
				.map(ou -> run.getAttestations().stream().filter(a -> a.getAttestationType() == Attestation.AttestationType.ORGANISATION_ATTESTATION
						&& ou.getUuid().equals(a.getResponsibleOuUuid())).findFirst().orElse(null))
				.filter(Objects::nonNull)
				.filter(o -> o.getVerifiedAt() == null)
				.map(u -> toShallowOrganisationDto(u.getCreatedAt(), u));
	}

	@Transactional
	public OrganisationAttestationDTO getAttestation(final String orgUnitUuid, final String currentUserUuid, final boolean undecidedUsersOnly) {
		var attestationType = Attestation.AttestationType.ORGANISATION_ATTESTATION;
		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				attestationType, orgUnitUuid).orElse(null);
		if (attestation == null) {
			return null;
		}
		return getAttestation(attestation, currentUserUuid, undecidedUsersOnly, attestationType);
	}

	@Transactional
	public OrganisationAttestationDTO getManagerDelegatedAttestation(final String orgUnitUuid, final String currentUserUuid, final boolean undecidedUsersOnly) {
		var attestationType = Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION;
		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				attestationType, orgUnitUuid).orElse(null);
		if (attestation == null) {
			return null;
		}
		return getAttestation(attestation, currentUserUuid, undecidedUsersOnly, attestationType);
	}


	@Transactional
	public OrganisationAttestationDTO getAttestation(final Attestation attestation, final String currentUserUuid, final boolean undecidedUsersOnly, Attestation.AttestationType attestationType) {
		final String orgUnitUuid = attestation.getResponsibleOuUuid();
		final List<AttestationUserRoleAssignment> userAssignments = userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(attestation.getCreatedAt(), orgUnitUuid);
		final List<AttestationOuRoleAssignment> organisationAssignments = ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(attestation.getCreatedAt(), orgUnitUuid);
		organisationAssignments.addAll(ouAssignmentsDao.listValidNotInheritedAssignmentsWithExceptedTilesForOu(attestation.getCreatedAt(), orgUnitUuid));
		final var ouName = userAssignments.stream().filter(r -> r.getResponsibleOuName() != null)
				.findFirst().map(AttestationUserRoleAssignment::getResponsibleOuName).orElse("");

		// Extract all roles that have been valid in between last attestation and now - so the person doing the attestation know
		// which roles have been active between attestations.
		final Attestation previousAttestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidAndVerifiedAtIsNotNullOrderByDeadlineDesc(
				attestationType, orgUnitUuid);
		final LocalDate previousAttestationDate = previousAttestation != null ? previousAttestation.getVerifiedAt().toLocalDate() : attestation.getCreatedAt();
		final List<AttestationUserRoleAssignment> temporaryAssignmentsSinceLastAttestation = userRoleAssignmentDao.
				listAssignmentsWhichHaveBeenValidBetweenByResponsibleOu(previousAttestationDate, attestation.getCreatedAt(), orgUnitUuid);
		final List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroupAssignments = orgUnitRoleGroups(organisationAssignments);
		final List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRoleAssignmentsPrItSystem = orgUnitUserRolesPrItSystem(attestation, organisationAssignments);
		return markCurrentUserReadonly(currentUserUuid,
				OrganisationAttestationDTO.builder()
						.createdAt(attestation.getCreatedAt())
						.attestationUuid(attestation.getUuid())
						.verifiedAt(attestation.getVerifiedAt() != null ? attestation.getVerifiedAt().toLocalDate() : null)
						.deadLine(attestation.getDeadline())
						.ouName(ouName)
						.ouUuid(orgUnitUuid)
						.orgUnitRolesVerified(isOrgVerified(attestation, orgUnitRoleGroupAssignments, orgUnitUserRoleAssignmentsPrItSystem))
						.roleAssignmentsSinceLastAttestation(buildRoleAssignmentChanges(temporaryAssignmentsSinceLastAttestation))
						.orgUnitRoleGroupAssignments(orgUnitRoleGroupAssignments)
						.orgUnitUserRoleAssignmentsPrItSystem(orgUnitUserRoleAssignmentsPrItSystem)
						.userAttestations(buildUserAttestations(userAssignments, attestation, undecidedUsersOnly, attestation.getCreatedAt()))
						.build()
		);
	}

	@Transactional
	public List<RoleAssignmentDTO> getUserRoleAssignments(final String attestationUuid, final String userUuid) {
		final Attestation attestation = attestationDao.findByUuid(attestationUuid);
		final List<AttestationUserRoleAssignment> userAssignments = userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(attestation.getCreatedAt(), attestation.getResponsibleOuUuid());

		return userAssignments.stream()
				.filter(a -> a.getUserUuid().equals(userUuid))
				.filter(a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT)
				.map(a -> RoleAssignmentDTO.builder()
						.roleType(a.getRoleGroupId() != null ? RoleType.ROLEGROUP : RoleType.USERROLE)
						.roleName(a.getRoleGroupId() != null ? a.getRoleGroupName() : a.getUserRoleName())
						.roleId(a.getRoleGroupId() != null ? a.getRoleGroupId() : a.getUserRoleId())
						.itSystemName(a.getItSystemName())
						.build())
				.distinct()
				.collect(Collectors.toList());
	}

	@Transactional
	public void verifyUser(final String orgUnitUuid, final String userUuid, final String performedByUserId, Attestation.AttestationType attestationType) {
		final User user = userService.getByUserId(performedByUserId);
		if (user.getUuid().equals(userUuid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User can not verify itself");
		}

		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				Attestation.AttestationType.ORGANISATION_ATTESTATION, orgUnitUuid).orElse(null);
		ensureUserEntryDoesntExist(userUuid, attestation);
        createUserEntry(attestation, userUuid, performedByUserId, null, false, new HashSet<String>(), new HashSet<String>());
		if (isOrganisationAttestationDone(attestation, attestation.getCreatedAt(), true, user)) {
			attestation.setVerifiedAt(ZonedDateTime.now());
		}
	}

	@Transactional
	public void rejectUser(final String orgUnitUuid, final String userUuid, final String performedByUserId, final String remarks, final List<RoleAssignmentDTO> notApprovedRoleAssignments, Attestation.AttestationType attestationType) {
		final User performingUser = userService.getByUserId(performedByUserId);
		if (performingUser.getUuid().equals(userUuid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User can not verify itself");
		}

		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				attestationType, orgUnitUuid).orElse(null);
		ensureUserEntryDoesntExist(userUuid, attestation);

        final Set<String> notApprovedUserRoles = getNotApprovedUserRoles(notApprovedRoleAssignments);
        final Set<String> notApprovedRoleGroups = getNotApprovedRoleGoups(notApprovedRoleAssignments);
        createUserEntry(attestation, userUuid, performedByUserId, remarks, false, notApprovedRoleGroups, notApprovedUserRoles);
		if (isOrganisationAttestationDone(attestation, attestation.getCreatedAt(), true, performingUser)) {
			attestation.setVerifiedAt(ZonedDateTime.now());
		}
		final User user = userService.getByUuid(userUuid);
		if (user != null) {
			emailNotificationService.sendRequestForChangeMail(
					userNameAndID(performingUser),
					userNameAndID(user), remarks, notApprovedRoleAssignments);
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
	public void requestAdRemoval(final String orgUnitUuid, final String userUuid, final String performedByUserId, Attestation.AttestationType attestationType) {
		final User performingUser = userService.getByUserId(performedByUserId);
		if (performingUser.getUuid().equals(userUuid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User can not verify itself");
		}

		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				attestationType, orgUnitUuid).orElse(null);
		ensureUserEntryDoesntExist(userUuid, attestation);
        createUserEntry(attestation, userUuid, performedByUserId, null, true, new HashSet<String>(), new HashSet<String>());
		if (isOrganisationAttestationDone(attestation, attestation.getCreatedAt(), true, performingUser)) {
			attestation.setVerifiedAt(ZonedDateTime.now());
		}
		final User user = userService.getByUuid(userUuid);
		if (user != null) {
			emailNotificationService.sendRequestForAdRemoval(userNameAndID(performingUser), userNameAndID(user), attestation.getResponsibleOuName());
		}
	}

	@Transactional
	public void acceptOrgUnitRoles(final String orgUnitUuid, final String performedByUserId, Attestation.AttestationType attestationType) {
		final User performingUser = userService.getByUserId(performedByUserId);
		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				attestationType, orgUnitUuid).orElse(null);
		ensureOrgRoleEntryDoesntExist(attestation);
		attestation.setOrganisationRolesAttestationEntry(
				organisationRoleAttestationEntryDao.save(OrganisationRoleAttestationEntry.builder()
						.attestation(attestation)
						.createdAt(ZonedDateTime.now())
						.performedByUserId(performedByUserId)
						.performedByUserUuid(userService.getByUserId(performedByUserId).getUuid())
						.remarks(null)
						.build()));
		if (isOrganisationAttestationDone(attestation, attestation.getCreatedAt(), true, performingUser)) {
			attestation.setVerifiedAt(ZonedDateTime.now());
		}
	}

	public boolean hasAttestationWithinTheLastYear(OrgUnit orgUnit) {
		List<Attestation> attestations = attestationDao.findByCreatedAtGreaterThanEqualAndResponsibleOuUuid(LocalDate.now().minusYears(1), orgUnit.getUuid());
		if (attestations == null || attestations.isEmpty()) {
			return false;
		}
		return true;
	}

	@Transactional
	public void rejectOrgUnitRoles(final String orgUnitUuid, final String performedByUserId, final String remarks, final List<RoleAssignmentDTO> notApprovedRoleAssignments, Attestation.AttestationType attestationType) {
		final Attestation attestation = attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
				attestationType, orgUnitUuid).orElse(null);
		ensureOrgRoleEntryDoesntExist(attestation);

		final Set<String> notApprovedUserRoles = getNotApprovedUserRoles(notApprovedRoleAssignments);
		final Set<String> notApprovedRoleGroups = getNotApprovedRoleGoups(notApprovedRoleAssignments);

		final User performingUser = userService.getByUserId(performedByUserId);
		final String performedByUserUuid = performingUser.getUuid();
		attestation.setOrganisationRolesAttestationEntry(
				organisationRoleAttestationEntryDao.save(OrganisationRoleAttestationEntry.builder()
						.attestation(attestation)
						.createdAt(ZonedDateTime.now())
						.performedByUserId(performedByUserId)
						.performedByUserUuid(performedByUserUuid)
						.rejectedUserRoleIds(notApprovedUserRoles)
						.rejectedRoleGroupIds(notApprovedRoleGroups)
						.remarks(remarks)
						.build()));
		if (isOrganisationAttestationDone(attestation, attestation.getCreatedAt(), true, performingUser)) {
			attestation.setVerifiedAt(ZonedDateTime.now());
		}
		userService.getOptionalByUuid(performedByUserUuid).ifPresent(user -> {
			emailNotificationService.sendRequestForChangeMail(
				userNameAndID(user),
				"Enhed("  + orgUnitService.getByUuid(orgUnitUuid).getName() + ")", remarks, notApprovedRoleAssignments);
		});
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

	private boolean isOrganisationAttestationDone(final Attestation attestation, final LocalDate when, boolean sendSummaryMailIfDone, User sendMailTo) {
		boolean activeDirectoryAttestationsDone = true;
		if (settingsService.isADAttestationEnabled()) {
			Set<String> approvedUsers = attestation.getOrganisationUserAttestationEntries().stream().map(BaseUserAttestationEntry::getUserUuid).collect(Collectors.toSet());
			activeDirectoryAttestationsDone = attestation.getUsersForAttestation().stream()
					.allMatch(u -> approvedUsers.contains(u.getUserUuid()));
		}
		final List<AttestationOuRoleAssignment> organisationAssignments =
				ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(attestation.getCreatedAt(), attestation.getResponsibleOuUuid());
		final List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroupAssignments = orgUnitRoleGroups(organisationAssignments);
		if (attestation.getOrganisationRolesAttestationEntry() == null && (!orgUnitRoleGroupAssignments.isEmpty() || !organisationAssignments.isEmpty())) {
			return false;
		}

		final List<String> processedUserUuid = attestation.getOrganisationUserAttestationEntries().stream()
				.map(BaseUserAttestationEntry::getUserUuid)
				.toList();
		final AttestationRun run = attestation.getAttestationRun();
		final List<AttestationUserRoleAssignment> userAssignments = userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(when, attestation.getResponsibleOuUuid()).stream()
				.filter(a -> (!run.isSensitive() && !run.isExtraSensitive()) || isSensitiveUser(attestation, a.getUserUuid()))
				.filter(a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT)
				.toList();
		boolean allDone = userAssignments.stream()
			.filter(distinctByKey(AttestationUserRoleAssignment::getUserUuid))
			.allMatch(a -> processedUserUuid.contains(a.getUserUuid())) && activeDirectoryAttestationsDone;

		if (allDone && sendSummaryMailIfDone && sendMailTo != null) {
			sendSummaryMail(attestation, sendMailTo);
		}

		return allDone;
	}

	private void sendSummaryMail(Attestation attestation, User sendMailTo) {
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY);
		if (template.isEnabled()) {

			String ouChanges = getOUChangeRequestsForMail(attestation);
			String userChanges = getUserChangeRequestsForMail(attestation);

			String title = template.getTitle();
			title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), sendMailTo.getName());
			title = title.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), attestation.getResponsibleOuName());
			title = title.replace(EmailTemplatePlaceholder.ATTESTATION_CHANGES_OU.getPlaceholder(), ouChanges);
			title = title.replace(EmailTemplatePlaceholder.ATTESTATION_CHANGES_USERS.getPlaceholder(), userChanges);

			String emailMessage = template.getMessage();
			emailMessage = emailMessage.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), sendMailTo.getName());
			emailMessage = emailMessage.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), attestation.getResponsibleOuName());
			emailMessage = emailMessage.replace(EmailTemplatePlaceholder.ATTESTATION_CHANGES_OU.getPlaceholder(), ouChanges);
			emailMessage = emailMessage.replace(EmailTemplatePlaceholder.ATTESTATION_CHANGES_USERS.getPlaceholder(), userChanges);

			log.info("Sending attestation summary email to " + sendMailTo.getEmail());
			emailQueueService.queueEmail(sendMailTo.getEmail(), title, emailMessage, template, null, null);
		}
		else {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
		}
	}

	private String getUserChangeRequestsForMail(Attestation attestation) {
		StringBuilder html = new StringBuilder();
		boolean hasChanges = false;

		for (OrganisationUserAttestationEntry entry : attestation.getOrganisationUserAttestationEntries()) {
			if (!hasUserChanges(entry)) {
				continue;
			}

			// user was probably removed already
			User user = userService.getByUuid(entry.getUserUuid());
			if (user == null) {
				continue;
			}

			hasChanges = true;

			html.append("<div style='margin-top: 30px;'>")
				.append("<strong>")
				.append(user.getName())
				.append("</strong>");

			if (entry.isAdRemoval()) {
				html.append("<div style='margin: 10px 0; color: #d9534f;'>")
					.append("<strong>AD-konto ønskes fjernet</strong>")
					.append("</div>");
			}

			appendRemarks(html, entry.getRemarks());

			appendUserRolesList(html, entry.getRejectedUserRoleIds());
			appendRoleGroupsList(html, entry.getRejectedRoleGroupIds());

			html.append("</div>");
		}

		return hasChanges ? html.toString() : "Ingen ændringsønsker";
	}

	private String getOUChangeRequestsForMail(Attestation attestation) {
		OrganisationRoleAttestationEntry entry = attestation.getOrganisationRolesAttestationEntry();

		if (entry == null || (entry.getRejectedUserRoleIds().isEmpty() &&
			entry.getRejectedRoleGroupIds().isEmpty() &&
			!StringUtils.hasLength(entry.getRemarks()))) {
			return "Ingen ændringsønsker";
		}

		StringBuilder html = new StringBuilder();

		appendRemarks(html, entry.getRemarks());
		appendUserRolesList(html, entry.getRejectedUserRoleIds());
		appendRoleGroupsList(html, entry.getRejectedRoleGroupIds());

		return html.toString();
	}

	private void appendRemarks(StringBuilder html, String remarks) {
		if (StringUtils.hasLength(remarks)) {
			html.append("<div style='margin-bottom: 15px;'>")
				.append("<strong>Bemærkninger:</strong><br/>")
				.append(remarks.replace("\n", "<br/>"))
				.append("</div>");
		}
	}

	private void appendUserRolesList(StringBuilder html, Set<String> userRoleIds) {
		if (userRoleIds == null || userRoleIds.isEmpty()) {
			return;
		}

		html.append("<div style='margin-bottom: 10px;'>")
			.append("<strong>Afviste jobfunktionsroller:</strong>")
			.append("<ul>");

		for (String userRoleIdStr : userRoleIds) {
			try {
				Long userRoleId = Long.parseLong(userRoleIdStr);
				UserRole userRole = userRoleService.getById(userRoleId);
				if (userRole != null) {
					html.append("<li>")
						.append(userRole.getItSystem().getName())
						.append(" - ")
						.append(userRole.getName())
						.append("</li>");
				}
			} catch (NumberFormatException e) {
				log.warn("Invalid user role ID format: {}", userRoleIdStr);
			}
		}

		html.append("</ul></div>");
	}

	private void appendRoleGroupsList(StringBuilder html, Set<String> roleGroupIds) {
		if (roleGroupIds == null || roleGroupIds.isEmpty()) {
			return;
		}

		html.append("<div style='margin-bottom: 10px;'>")
			.append("<strong>Afviste rollebuketter:</strong>")
			.append("<ul>");

		for (String roleGroupIdStr : roleGroupIds) {
			try {
				Long roleGroupId = Long.parseLong(roleGroupIdStr);
				RoleGroup roleGroup = roleGroupService.getById(roleGroupId);
				if (roleGroup != null) {
					html.append("<li>").append(roleGroup.getName()).append("</li>");
				}
			} catch (NumberFormatException e) {
				log.warn("Invalid role group ID format: {}", roleGroupIdStr);
			}
		}

		html.append("</ul></div>");
	}

	private boolean hasUserChanges(OrganisationUserAttestationEntry entry) {
		return entry.isAdRemoval()
				|| StringUtils.hasLength(entry.getRemarks())
				|| !CollectionUtils.isEmpty(entry.getRejectedUserRoleIds())
				|| !CollectionUtils.isEmpty(entry.getRejectedRoleGroupIds());
	}

	private void createUserEntry(final Attestation attestation, final String userUuid, final String performedByUserId, final String remarks,
                                 final boolean adRemoval, Set<String> notApprovedRoleGroups, Set<String> notApprovedUserRoles) {
		final OrganisationUserAttestationEntry entry =
				organisationUserAttestationEntryDao.save(
						OrganisationUserAttestationEntry.builder()
								.attestation(attestation)
								.performedByUserId(performedByUserId)
								.performedByUserUuid(userService.getByUserId(performedByUserId).getUuid())
								.userUuid(userUuid)
								.adRemoval(adRemoval)
								.remarks(remarks)
                                .rejectedRoleGroupIds(notApprovedRoleGroups)
                                .rejectedUserRoleIds(notApprovedUserRoles)
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
	OrganisationAttestationDTO toShallowOrganisationDto(final LocalDate when, final Attestation attestationOrganisation) {
		final List<AttestationUserRoleAssignment> userRoleAssignments = userRoleAssignmentDao
				.listValidAssignmentsByResponsibleOu(when, attestationOrganisation.getResponsibleOuUuid());
		final List<AttestationOuRoleAssignment> organisationAssignments = ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(attestationOrganisation.getCreatedAt(), attestationOrganisation.getResponsibleOuUuid());
		final List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroupAssignments = orgUnitRoleGroups(organisationAssignments);
		final List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRoleAssignmentsPrItSystem = orgUnitUserRolesPrItSystem(attestationOrganisation, organisationAssignments);
		return OrganisationAttestationDTO.builder()
				.createdAt(attestationOrganisation.getCreatedAt())
				.attestationUuid(attestationOrganisation.getUuid())
				.ouUuid(attestationOrganisation.getResponsibleOuUuid())
				.ouName(attestationOrganisation.getResponsibleOuName())
				.orgUnitRolesVerified(isOrgVerified(attestationOrganisation, orgUnitRoleGroupAssignments, orgUnitUserRoleAssignmentsPrItSystem))
				.deadLine(attestationOrganisation.getDeadline())
				.orgUnitRoleGroupAssignments(orgUnitRoleGroupAssignments)
				.orgUnitUserRoleAssignmentsPrItSystem(orgUnitUserRoleAssignmentsPrItSystem)
				.userAttestations(buildUserAttestations(userRoleAssignments, attestationOrganisation, false, when))
				.build();
	}

	static boolean isOrgVerified(Attestation attestationOrganisation, final List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroupAssignments,
								 final List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRoleAssignmentsPrItSystem) {
		if (orgUnitRoleGroupAssignments.isEmpty() && orgUnitUserRoleAssignmentsPrItSystem.isEmpty()) {
			return true;
		} else {
			return attestationOrganisation.getOrganisationRolesAttestationEntry() != null;
		}
	}

	List<RoleAssignmentSinceLastAttestationDTO> buildRoleAssignmentChanges(final List<AttestationUserRoleAssignment> assignments) {
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
	public record UserAttestationContext(String userUuid, String userName, String userId, String roleUuid, String responsibleOuUuid, String roleOuName) {}
	List<UserAttestationDTO> buildUserAttestations(final List<AttestationUserRoleAssignment> assignments, final Attestation attestation,
												   boolean undecidedUsersOnly, final LocalDate when) {

		List<UserAttestationContext> userAttestationContexts = null;
		Set<String> assignmentsUserUuids = assignments.stream().map(AttestationUserRoleAssignment::getUserUuid).collect(Collectors.toSet());
		userAttestationContexts = assignments.stream()
				.filter(distinctByKey(AttestationUserRoleAssignment::getUserUuid))
				.map(attestationUserRoleAssignment -> new UserAttestationContext(attestationUserRoleAssignment.getUserUuid(), attestationUserRoleAssignment.getUserName(), attestationUserRoleAssignment.getUserId(), attestationUserRoleAssignment.getRoleOuUuid(), attestationUserRoleAssignment.getResponsibleOuUuid(), attestationUserRoleAssignment.getRoleOuName()))
				.toList();

		// If AD-Attestation is enabled, we should take a look at Attestation OrganisationUserAttestations and check if there is users without assignments that should also be attestated
		if (settingsService.isADAttestationEnabled()) {
			List<UserAttestationContext> additionalContexts = attestation.getUsersForAttestation().stream()
				.filter(u -> !assignmentsUserUuids.contains(u.getUserUuid()))
				.map(attestationUser -> {
					String userUuid = attestationUser.getUserUuid();
					User user = userService.getOptionalByUuid(userUuid).orElse(null);
					return new UserAttestationContext(
						userUuid,
						user == null ? null : user.getName(),
						user == null ? null : user.getUserId(),
						attestation.getResponsibleOuUuid(),
						attestation.getResponsibleOuUuid(),
						null
					);
				})
				.toList();
			userAttestationContexts = Stream.concat(userAttestationContexts.stream(), additionalContexts.stream()).toList();
		}

		final var userRoleGroupAssignments = assignments.stream()
				.filter(p -> p.getRoleGroupId() != null && p.getRoleGroupId() != 0)
				.toList();
		final var userRoleAssignments = assignments.stream()
				.filter(p -> p.getRoleGroupId() == null || p.getRoleGroupId() == 0)
				.toList();
		final AttestationRun run = attestation.getAttestationRun();
		List<UserAttestationDTO> userAttestationDTOs = userAttestationContexts.stream()
				.filter(a -> (!run.isSensitive() && !run.isExtraSensitive()) || // if this is sensitive, we only want users with sensitive roles
						isSensitiveUser(attestation, a.userUuid))
				.map(u -> {
					final OrganisationUserAttestationEntry entry = findUserAttestation(attestation, u.userUuid).orElse(null);
					// Find assignments where the it-system responsible, is responsible for attestation
					final List<AttestationUserRoleAssignment> systemResponseAssignments = userRoleAssignmentDao
							.listValidAssignmentsForUserHandledByItSystemResponsible(when, u.userUuid);
					// Find assignments that another department is responsible for
					final List<AttestationUserRoleAssignment> otherDepartmentAssignments =
						userRoleAssignmentDao.listValidAssignmentsForUserWhereResponsibleOUIsNot(when, u.responsibleOuUuid, u.roleUuid);
					// Now group all "other" roles
					final List<AttestationUserRoleAssignment> otherRoles = Stream.concat(
									Stream.concat(userRoleAssignments.stream(), otherDepartmentAssignments.stream().filter(a -> a.getRoleGroupId() == null)),
									systemResponseAssignments.stream())
							.collect(Collectors.toList());
					final List<AttestationUserRoleAssignment> otherRoleGroups = Stream.concat(userRoleGroupAssignments.stream(),
							otherDepartmentAssignments.stream().filter(a -> a.getRoleGroupId() != null)).toList();
					// Inherited OU assignments and assignments where the it-system responsible is responsible should go into doNotVerify..
					final List<UserRoleItSystemDTO> doNotVerifyUserRolesPrItSystem = userRolesPrItSystem(u.userUuid, otherRoles,
							a -> ((a.getAssignedThroughType() == AssignedThroughType.ORGUNIT && a.isInherited()) || a.getResponsibleUserUuid() != null));
					final List<RoleGroupDTO> doNotVerifyRoleGroups = userRoleGroups(u.userUuid, otherRoleGroups,
							a -> a.getAssignedThroughType() == AssignedThroughType.ORGUNIT && a.isInherited());

					String userPositionsCached = attestationUserService.getUserPositionsCached(u.userUuid, u.roleUuid);
					if (!Objects.equals(u.roleUuid, attestation.getResponsibleOuUuid())) {
						// Add the OU in case it differs from current
						userPositionsCached += "(" + u.roleOuName + ")";
					}

					return UserAttestationDTO.builder()
							.userName(u.userName)
							.userId(u.userId)
							.userUuid(u.userUuid)
							.position(userPositionsCached)
							.verifiedByUserId(entry != null ? entry.getPerformedByUserId() : null)
							.remarks(entry != null ? entry.getRemarks() : null)
							.doNotVerifyRoleGroups(doNotVerifyRoleGroups)
							.doNotVerifyUserRolesPrItSystem(doNotVerifyUserRolesPrItSystem)
							.userRolesPrItSystem(userRolesPrItSystem(u.userUuid, userRoleAssignments, a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT))
							.roleGroups(userRoleGroups(u.userUuid, userRoleGroupAssignments, a -> a.getAssignedThroughType() == AssignedThroughType.DIRECT))
							.adRemoval(entry != null && entry.isAdRemoval())
							.isPrimary(attestationUserService.hasPrimaryPositionIn(u.userUuid, u.roleUuid))
							.build();
				})
				.filter(u -> !undecidedUsersOnly || (u.getRemarks() == null && u.getVerifiedByUserId() == null))
				.toList();

				// If delegated attestation, filter for delegated managers
				if (attestation.getAttestationType() == Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION) {
					User currentUser = userService.getByUserId(SecurityUtil.getUserId());
					List<String> delegatedForManagersUuid = managerDelegateAttestationService.getManagedUsersForDelegate(currentUser).stream()
							.map(User::getUuid)
							.toList();
					userAttestationDTOs = userAttestationDTOs.stream()
							.filter(at -> delegatedForManagersUuid.contains(at.getUserUuid()))
							.toList();

				}

		boolean anyPrimary = userAttestationDTOs.stream().anyMatch(UserAttestationDTO::isPrimary);
		return userAttestationDTOs.stream()
				.filter(userAttestationDTO -> {
					//If any assignment, return true
					if (!userAttestationDTO.getUserRolesPrItSystem().isEmpty() || !userAttestationDTO.getRoleGroups().isEmpty()) {
						return true;
					}
					//If AD attestation is enabled...
					if (settingsService.isADAttestationEnabled()) {
						//if this is the dto for the primary position, include
						if (anyPrimary && userAttestationDTO.isPrimary()) {
							return true;
							//If a primary position exists, but this is not it, do not include. If no primary exist include all
						} else {
							return !anyPrimary;
						}
					}
					//Do not include if there is no assignments and AD is not enabled
					return false;
				})
				.toList();
	}

	private static Optional<OrganisationUserAttestationEntry> findUserAttestation(final Attestation attestation, String userUuid) {
		return attestation.getOrganisationUserAttestationEntries().stream()
				.filter(a -> a.getUserUuid().equals(userUuid))
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

	List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRolesPrItSystem(final Attestation attestation,
																		  final List<AttestationOuRoleAssignment> assignments) {
		final var orgRoleAssignments = assignments.stream()
				.filter(p -> p.getRoleGroupId() == null || p.getRoleGroupId() == 0)
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

	List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroups(final List<AttestationOuRoleAssignment> assignments) {
		final var orgRoleGroupAssignments = assignments.stream()
				.filter(p -> p.getAssignedThroughType() == AssignedThroughType.DIRECT || p.getAssignedThroughType() == AssignedThroughType.ORGUNIT)
				.filter(p -> p.getRoleGroupId() != null && p.getRoleGroupId() != 0)
				.toList();

		return orgRoleGroupAssignments.stream()
				.filter(distinctByKey(a -> a.getRoleGroupId() + ":" + a.getTitleUuids()))
				.map(a -> {
							final List<String> titles = a.getTitleUuids().stream()
									.map(t -> titleDao.findById(t).map(Title::getName).orElse(t))
									.toList();
							final List<String> functions = a.getFunctionUuids().stream()
									.map(f -> functionDao.findById(f).map(Function::getName).orElse(f))
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
									.exceptedTitles(a.getExceptedTitleUuids().stream().map(et -> titleDao.findById(et).map(Title::getName).orElse(et)).toList())
									.groupId(a.getRoleGroupId())
									.userRoles(toOuUserAssignmentDTOs(orgRoleGroupAssignments, a.getRoleGroupId(), a.getTitleUuids()))
									.inherit(!a.isInherited() && a.getAssignedThroughType() == AssignedThroughType.ORGUNIT)
									.manager(a.isManager())
									.substitutes(a.isSubstitutes())
									.functions(functions)
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
						.postponedConstraints(a.getPostponedConstraints())
						.build())
				.collect(Collectors.toList());
	}

	private List<OrgUnitUserRoleAssignmentDTO> toOuUserAssignmentDTOs(final List<AttestationOuRoleAssignment> assignments) {
		return assignments.stream()
				.map(this::toOuAssignmentDTO)
				.collect(Collectors.toList());
	}

	private List<OrgUnitUserRoleAssignmentDTO> toOuUserAssignmentDTOs(final List<AttestationOuRoleAssignment> assignments, final long roleGroupId, final List<String> titleUuids) {
		return assignments.stream()
			.filter(a -> roleGroupId == a.getRoleGroupId())
			.filter(a -> Objects.equals(a.getTitleUuids(), titleUuids))
			.map(this::toOuAssignmentDTO)
			.collect(Collectors.toList());
	}

	private OrgUnitUserRoleAssignmentDTO toOuAssignmentDTO(final AttestationOuRoleAssignment assignment) {
		final List<String> titles = assignment.getTitleUuids().stream()
				.map(t -> titleDao.findById(t).map(Title::getName).orElse(t))
				.toList();
		final List<String> functions = assignment.getFunctionUuids().stream()
				.map(f -> functionDao.findById(f).map(Function::getName).orElse(f))
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
				.exceptedTitles(assignment.getExceptedTitleUuids().stream().map(et -> titleDao.findById(et).map(Title::getName).orElse(et)).toList())
				.roleDescription(assignment.getRoleDescription())
				.roleName(assignment.getRoleName())
				.manager(assignment.isManager())
				.substitutes(assignment.isSubstitutes())
				.functions(functions)
				.build();
	}


	private static String userNameAndID(final User user) {
		return user.getName() + "(" + user.getUserId() + ")";
	}

}
