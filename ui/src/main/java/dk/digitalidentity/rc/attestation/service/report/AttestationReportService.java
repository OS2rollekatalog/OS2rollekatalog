package dk.digitalidentity.rc.attestation.service.report;

import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.isSensitiveUser;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.ADAttestationUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.ITSystemRoleBuildAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.collections4.ListUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AttestationReportService {

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private AttestationUserRoleAssignmentDao attestationUserRoleAssignmentDao;

	@Autowired
	private AttestationCachedUserService cachedUserService;

	@Autowired
	private AttestationReportRowFilterService rowFilterService;

	@Autowired
	private AttestationReportContextService attestationReportContextService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private AttestationDao attestationDao;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@PersistenceContext
	private EntityManager entityManager;

	private static class VerificationAndAttestationInformationDTO {
		private AttestationStatus status = AttestationStatus.NOT_VERIFIED;
		private LocalDate verifiedAt;
		private String verifiedByName;
		private String verifiedByUserId;
		private String remark;
		private LocalDate attestationCreatedAt;
		private boolean attestationInProgress;
	}

	@Transactional
	public Map<String, Object> getOrgUnitReportModel(OrgUnit orgUnit, Locale locale, LocalDate since, LocalDate to) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getOrgUnitRowIds(since, to, orgUnit), since, to, rowFilterService.filter()));
		model.put("orgUnitName", orgUnit.getName());
		model.put("from", since);
		model.put("to", to);
		model.put("messageSource", messageSource);
		model.put("locale", locale);
		return model;
	}

	@Transactional
	public Map<String, Object> getAllReportModel(Locale locale, LocalDate since, LocalDate to) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which takes up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getAllRowIds(since, to), since, to, rowFilterService.filter()));
		model.put("orgUnitName", "Alle");
		model.put("from", since);
		model.put("to", to);
		model.put("messageSource", messageSource);
		model.put("locale", locale);
		return model;
	}

	@Transactional
	public Map<String, Object> getAllRolesReportModel(Locale locale, LocalDate since, LocalDate to) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which takes up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("from", since);
		model.put("to", to);
		model.put("messageSource", messageSource);
		model.put("locale", locale);
		model.put("itSystemRoleAttestationDTO", getITSystemRoleBuildAttestation(since, to));
		return model;
	}

	public Map<String, Object> getAuditReportModel(Locale locale, LocalDate since, LocalDate to, AttestationRun attestationRun) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which takes up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getAllRowIds(since, to), since, to, rowFilterService.filter(), attestationRun));
		model.put("includeUsers", settingsService.isADAttestationEnabled());
		model.put("adUsersAttestation", getADUserAttestation(attestationRun));
		model.put("orgUnitName", "Alle");
		model.put("from", since);
		model.put("to", to);
		model.put("messageSource", messageSource);
		model.put("locale", locale);

		return model;
	}

	@Transactional
	public Map<String, Object> getItSystemReportModel(ItSystem itSystem, Locale locale, LocalDate since, LocalDate to) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getItSystemRowIds(since, to, itSystem), since, to, rowFilterService.filter()));
		model.put("itSystemName", itSystem.getName());
		model.put("from", since);
		model.put("to", LocalDate.now());
		model.put("messageSource", messageSource);
		model.put("locale", locale);

		return model;
	}

	private List<Long> getAllRowIds(final LocalDate since, final LocalDate when) {
		final LocalDate from = since != null ? since : when.minusYears(1);
		final LocalDate to = when.plusDays(1);
		return attestationUserRoleAssignmentDao.listAssignmentIdsValidBetween(from, to);
	}

	private List<Long> getOrgUnitRowIds(final LocalDate since, final LocalDate when, final OrgUnit orgUnit) {
		final LocalDate from = since != null ? since : when.minusYears(1);
		final LocalDate to = when.plusDays(1);
		return attestationUserRoleAssignmentDao.listAssignmentIdsValidBetweenForRoleOu(orgUnit.getUuid(), from, to);

	}

	public List<Long> getItSystemRowIds(final LocalDate since, final LocalDate when, final ItSystem itSystem) {
		final LocalDate from = since != null ? since : when.minusYears(1);
		final LocalDate to = when.plusDays(1);
		return attestationUserRoleAssignmentDao.listAssignmentValidBetweenForItSystem(itSystem.getId(), from, to);
	}

	/**
	 * The attestations every row in the report is matched against - built once per report, not per page.
	 */
	public AttestationReportContextService.AttestationReportContext reportContext(final LocalDate since, final LocalDate when, final AttestationRun run) {
		return run != null
				? attestationReportContextService.createContextForRun(run)
				: attestationReportContextService.createContext(since != null ? since : when.minusYears(1));
	}

	@Transactional
	public List<RoleAssignmentReportRowDTO> getUserRoleRows(final LocalDate since, final LocalDate when, List<Long> rowIds) {
		return getUserRoleRows(since, when, rowIds, reportContext(since, when, null));
	}

	@Transactional
	public List<RoleAssignmentReportRowDTO> getUserRoleRows(final LocalDate since, final LocalDate when, List<Long> rowIds,
			final AttestationReportContextService.AttestationReportContext context) {
		final List<List<Long>> partitionedRowIds = ListUtils.partition(rowIds, 1000);

		return partitionedRowIds.stream()
				.flatMap(page -> attestationUserRoleAssignmentDao.findByIdIn(page).stream())
				.map(a -> {
					final VerificationAndAttestationInformationDTO verificationInformation = findVerificationAndAttestationInformation(a, context, when);
					return toRow(a, verificationInformation, when);
				})
				.collect(Collectors.toList());
	}

	private RoleAssignmentReportRowDTO toRow(final AttestationUserRoleAssignmentDto assignment, final VerificationAndAttestationInformationDTO verificationInformation, final LocalDate now) {
		return RoleAssignmentReportRowDTO.builder()
				.itSystemName(assignment.getItSystemName())
				.itSystemId(assignment.getItSystemId())
				.assignedFrom(assignment.getValidFrom())
				.assignedTo(assignment.getValidTo())
				.assignedThrough(assignment.getAssignedThroughName())
				.assignedThroughType(getAssignedThroughTypeName(assignment.getAssignedThroughType()))
				// Derived, because the stored flag is hardcoded to false in UserAssignmentsUpdaterJdbc
				.inherited(assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT)
				.responsibleOu(assignment.getResponsibleOuName())
				.responsibleUserNames(
					getResponsibleUsers(assignment)
					)
				.attestationStatus(verificationInformation.status)
				.verifiedAt(verificationInformation.verifiedAt)
				.verifiedByUserId(verificationInformation.verifiedByUserId)
				.verifiedByName(verificationInformation.verifiedByName)
				.roleGroupName(assignment.getRoleGroupName())
				.userRoleName(assignment.getUserRoleName())
				.postponedConstraints(assignment.getPostponedConstraints())
				.remark(verificationInformation.remark)
				.userName(assignment.getUserName())
				.userUserId(assignment.getUserId())
				.position(resolveUserPositions(assignment))
				.status(assignment.getValidTo() == null || assignment.getValidTo().isAfter(now)
						? RoleStatus.ACTIVE
						: RoleStatus.INACTIVE)
				// Direct assignments have no role OU; their responsible OU is derived from the assignment's OU
				.orgUnit(assignment.getRoleOuName() != null ? assignment.getRoleOuName() : assignment.getResponsibleOuName())
				.attestationCreatedAt(verificationInformation.attestationCreatedAt)
				.attestationInProgress(verificationInformation.attestationInProgress)
				.validFrom(assignment.getValidFrom())
				.validTo(assignment.getValidTo())
				.originallyAssignedFrom(assignment.getAssignedFrom())
				.build();
	}

	/**
	 * The user's position(s) in the org unit responsible for the attestation, falling back to all positions.
	 * The role's own org unit is already reported in the "orgUnit" column.
	 */
	private String resolveUserPositions(final AttestationUserRoleAssignmentDto assignment) {
		if (assignment.getResponsibleOuUuid() != null) {
			final String positionsInOu = cachedUserService.getUserPositionsCached(assignment.getUserUuid(), assignment.getResponsibleOuUuid());
			if (positionsInOu != null && !positionsInOu.isBlank()) {
				return positionsInOu;
			}
		}
		return cachedUserService.getAllUserPositionsCached(assignment.getUserUuid());
	}

	private static String getAssignedThroughTypeName(final AssignedThroughType assignedThroughType) {
		return switch (assignedThroughType) {
            case DIRECT -> "Direkte";
            case POSITION -> "Stilling";
            case ORGUNIT -> "Enhed";
            case TITLE -> "Titel";
        };
	}

	/**
	 * This "rather lovely" method will figure out which attestation the supplied assignment belongs to and calculate
	 * the needed verification information for a report.
	 */
	private VerificationAndAttestationInformationDTO findVerificationAndAttestationInformation(final AttestationUserRoleAssignmentDto assignment,
																   final AttestationReportContextService.AttestationReportContext context,
																   final LocalDate when) {
		VerificationAndAttestationInformationDTO verificationInformation = new VerificationAndAttestationInformationDTO();
		if (assignment.getResponsibleCollectionId() != null) {
			// The it-system responsible is asked about one collection at a time, so the collection has to match too
			verificationInformation = newestDecision(verificationInformation,
					context.getItSystemUserAttestations().stream()
							.filter(a -> Objects.equals(a.getItSystemId(), assignment.getItSystemId()))
							.filter(a -> Objects.equals(a.getResponsibleCollectionId(), assignment.getResponsibleCollectionId()))
							.filter(a -> covers(a, assignment, when)),
					attestation -> findVerificationInformationForSystemUserAttestation(assignment, attestation));
		}
		if (assignment.getResponsibleOuUuid() != null && verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
			if (assignedOnOrgUnit(assignment)) {
				// Attested as part of the role setup in the ou the role is assigned on. Use assignedThroughUuid, not
				// roleOuUuid, which held the user's own ou on rows written before the updater was rewritten - except
				// for a title assignment, which carries the title there and only has the ou in roleOuUuid.
				final String assigningOuUuid = assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT
						? assignment.getAssignedThroughUuid()
						: assignment.getRoleOuUuid();
				verificationInformation = newestDecision(verificationInformation,
						context.getOrganisationRolesAttestations().stream()
								.filter(a -> Objects.equals(a.getResponsibleOuUuid(), assigningOuUuid))
								.filter(a -> covers(a, assignment, when)),
						this::findVerificationInformationForInheritedOUAttestation);
			}
			else {
				// Assigned to the user, also when it came with a role group, so the manager decides on the user
				verificationInformation = newestDecision(verificationInformation,
						context.getOrganisationRolesAttestations().stream()
								.filter(a -> Objects.equals(a.getResponsibleOuUuid(), assignment.getResponsibleOuUuid()))
								// A sensitive attestation only asks about users with sensitive roles - membership is rebuilt nightly, a decision is not
								.filter(a -> isSensitiveUser(a, assignment.getUserUuid()) || lookupOrganisationUserEntry(a, assignment) != null)
								.filter(a -> covers(a, assignment, when)),
						attestation -> findVerificationInformationForOUAttestation(assignment, attestation));
			}
		}
		if (assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT && verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
			// Se if an it-system responsible verified a parent ou
			verificationInformation = supplementaryDecision(verificationInformation,
					context.getItSystemUserAttestations().stream()
							.filter(a -> Objects.equals(a.getItSystemId(), assignment.getItSystemId()) && a.getResponsibleCollectionId() != null)
							.filter(a -> covers(a, assignment, when)),
					attestation -> findVerificationInformationForSystemOuAttestation(assignment, attestation));
		}
		return verificationInformation;
	}

	/**
	 * A role assigned on an org unit is decided once as part of that org unit's role setup, everything else per user,
	 * see {@link dk.digitalidentity.rc.attestation.service.OrganisationAttestationService#isManagerResponsibleFor}.
	 */
	private static boolean assignedOnOrgUnit(final AttestationUserRoleAssignmentDto assignment) {
		return assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT
				|| assignment.getAssignedThroughType() == AssignedThroughType.TITLE;
	}

	/**
	 * Verification information from the newest attestation that decided on the assignment, else from the newest one
	 * that covered it undecided - an attestation in progress must not hide the decision from the previous one.
	 */
	private VerificationAndAttestationInformationDTO newestDecision(final VerificationAndAttestationInformationDTO fallback,
			final Stream<Attestation> covering,
			final Function<Attestation, VerificationAndAttestationInformationDTO> lookup) {
		VerificationAndAttestationInformationDTO newest = null;
		for (final Attestation attestation : newestFirst(covering)) {
			final VerificationAndAttestationInformationDTO candidate = lookup.apply(attestation);
			if (candidate.status != AttestationStatus.NOT_VERIFIED) {
				return candidate;
			}
			if (newest == null) {
				newest = candidate;
			}
		}
		return newest != null ? newest : fallback;
	}

	/**
	 * A supplementary lookup answers on behalf of someone else than the assignment's responsible, so it only takes over
	 * when it actually decided - an assignment the it-system responsible was asked about is already marked by its collection.
	 */
	private VerificationAndAttestationInformationDTO supplementaryDecision(final VerificationAndAttestationInformationDTO fallback,
			final Stream<Attestation> covering,
			final Function<Attestation, VerificationAndAttestationInformationDTO> lookup) {
		final VerificationAndAttestationInformationDTO result = newestDecision(fallback, covering, lookup);
		return result.status == AttestationStatus.NOT_VERIFIED ? fallback : result;
	}

	private static List<Attestation> newestFirst(final Stream<Attestation> covering) {
		return covering.sorted(Comparator.comparing(Attestation::getCreatedAt).reversed()).toList();
	}

	/**
	 * An attestation only covers the assignments that were valid the day it was created - that snapshot is what its
	 * responsible was asked about. Same validity predicate as
	 * {@link dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao#listValidAssignmentsByResponsibleOu}.
	 */
	private static boolean covers(final Attestation attestation, final AttestationUserRoleAssignmentDto assignment, final LocalDate when) {
		final LocalDate createdAt = attestation.getCreatedAt();
		return !createdAt.isAfter(when) // an attestation from after the reported period is not part of it
				&& assignment.getValidFrom() != null
				&& !assignment.getValidFrom().isAfter(createdAt)
				&& (assignment.getValidTo() == null || assignment.getValidTo().isAfter(createdAt));
	}

	private VerificationAndAttestationInformationDTO findVerificationInformationForInheritedOUAttestation(final Attestation attestation) {
		final VerificationAndAttestationInformationDTO result = new VerificationAndAttestationInformationDTO();
		if (attestation != null) {
			if (attestation.getOrganisationRolesAttestationEntry() != null) {
				final OrganisationRoleAttestationEntry entry = attestation.getOrganisationRolesAttestationEntry();
				if (entry.getRemarks() != null) {
					result.status = AttestationStatus.REMARKS;
				}
				else {
					result.status = AttestationStatus.APPROVED;
				}
				result.remark = entry.getRemarks();
				result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
				result.verifiedByUserId = entry.getPerformedByUserId();
				result.verifiedAt = entry.getCreatedAt().toLocalDate();
			}

			markAttestation(result, attestation);
		}

		return result;
	}

	private VerificationAndAttestationInformationDTO findVerificationInformationForOUAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		final VerificationAndAttestationInformationDTO result = new VerificationAndAttestationInformationDTO();
		final OrganisationUserAttestationEntry entry = lookupOrganisationUserEntry(attestation, assignment);
		if (entry != null) {
			result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
			result.verifiedByUserId = entry.getPerformedByUserId();
			result.verifiedAt = entry.getCreatedAt().toLocalDate();
			if (entry.getRemarks() != null) {
				boolean setRemarks = (assignment.getRoleGroupId() != null && entry.getRejectedRoleGroupIds().contains(assignment.getRoleGroupId() + "")) ||
						(assignment.getRoleGroupId() == null && entry.getRejectedUserRoleIds().contains(assignment.getUserRoleId() + ""));

				if (setRemarks) {
					result.remark = entry.getRemarks();
					result.status = AttestationStatus.REMARKS;
				} else {
					result.status = AttestationStatus.APPROVED;
				}
			} else if (entry.isAdRemoval()) {
				result.status = AttestationStatus.DELETE;
			} else {
				result.status = AttestationStatus.APPROVED;
			}
		}

		markAttestation(result, attestation);

		return result;
	}

	private static void markAttestation(final VerificationAndAttestationInformationDTO result, final Attestation attestation) {
		result.attestationCreatedAt = attestation.getCreatedAt();
		result.attestationInProgress = attestation.getVerifiedAt() == null;
	}

	private VerificationAndAttestationInformationDTO findVerificationInformationForSystemUserAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		VerificationAndAttestationInformationDTO result = new VerificationAndAttestationInformationDTO();
		final ItSystemUserAttestationEntry entry = lookupItSystemUserEntry(attestation, assignment);
		if (entry != null) {
			result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
			result.verifiedByUserId = entry.getPerformedByUserId();
			result.verifiedAt = entry.getCreatedAt().toLocalDate();

			if (entry.getRemarks() != null) {
				boolean setRemarks = (assignment.getRoleGroupId() != null && entry.getRejectedRoleGroupIds().contains(assignment.getRoleGroupId() + "")) ||
						(assignment.getRoleGroupId() == null && entry.getRejectedUserRoleIds().contains(assignment.getUserRoleId() + ""));

				if (setRemarks) {
					result.remark = entry.getRemarks();
					result.status = AttestationStatus.REMARKS;
				} else {
					result.status = AttestationStatus.APPROVED;
				}
			} else {
				result.status = AttestationStatus.APPROVED;
			}
		}

		markAttestation(result, attestation);

		return result;
	}

	private VerificationAndAttestationInformationDTO findVerificationInformationForSystemOuAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		VerificationAndAttestationInformationDTO result = new VerificationAndAttestationInformationDTO();
		final ItSystemOrganisationAttestationEntry entry = lookupItSystemOuEntry(attestation, assignment);
		if (entry != null) {
			result.status = entry.getRemarks() != null
					? AttestationStatus.REMARKS
					: AttestationStatus.APPROVED;
			result.remark = entry.getRemarks();
			result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
			result.verifiedByUserId = entry.getPerformedByUserId();
			result.verifiedAt = entry.getCreatedAt().toLocalDate();
		}

		markAttestation(result, attestation);

		return result;
	}

	private List<ADAttestationUserDTO> getADUserAttestation(AttestationRun attestationRun) {
		List<ADAttestationUserDTO> result = new ArrayList<>();
		if (settingsService.isADAttestationEnabled()) {
			for (Attestation attestation : attestationRun.getAttestations()) {
				List<User> users = userService.getAllByUuidIn(attestation.getUsersForAttestation().stream().map(AttestationUser::getUserUuid).collect(Collectors.toSet()));
				List<String> responsibleUsers = attestationResponsibleCollectionDao.findById(attestation.getResponsibleCollectionId()).stream()
					.flatMap(u -> u.getUsersUuid().stream())
					.toList();
				result.addAll(users.stream().map(u -> ADAttestationUserDTO.builder()
						.uuid(u.getUuid())
						.name(u.getName())
						.username(u.getUserId())
						.verifiedAt(attestation.getVerifiedAt() == null ? null : LocalDate.from(attestation.getVerifiedAt()))
						.responsibleOU(attestation.getResponsibleOuName())
						.responsibleUserNames(responsibleUsers)
						.build())
					.toList());
			}
		}
		return result;
	}

	private static ItSystemOrganisationAttestationEntry lookupItSystemOuEntry(final Attestation attestation,
																			  final AttestationUserRoleAssignmentDto assignment) {
		if (attestation == null || attestation.getItSystemOrganisationAttestationEntries().isEmpty()) {
			return null;
		}
		return attestation.getItSystemOrganisationAttestationEntries().stream()
				.filter(a -> Objects.equals(a.getOrganisationUuid(), assignment.getAssignedThroughUuid()))
				.findFirst()
				.orElse(null);
	}

	private static ItSystemUserAttestationEntry lookupItSystemUserEntry(final Attestation attestation,
			final AttestationUserRoleAssignmentDto assignment) {
		if (attestation == null || attestation.getItSystemUserAttestationEntries().isEmpty()) {
			return null;
		}
		return attestation.getItSystemUserAttestationEntries().stream()
				.filter(a -> Objects.equals(a.getUserUuid(), assignment.getUserUuid()))
				.findFirst()
				.orElse(null);
	}

	private static OrganisationUserAttestationEntry lookupOrganisationUserEntry(final Attestation attestation,
																				final AttestationUserRoleAssignmentDto assignment) {
		if (attestation == null || attestation.getOrganisationUserAttestationEntries().isEmpty()) {
			return null;
		}
		return attestation.getOrganisationUserAttestationEntries().stream()
				.filter(a -> Objects.equals(a.getUserUuid(), assignment.getUserUuid()))
				.findFirst()
				.orElse(null);
	}

	private List<ITSystemRoleBuildAttestationDTO> getITSystemRoleBuildAttestation(LocalDate from, LocalDate to) {
		List<ITSystemRoleBuildAttestationDTO> result = new ArrayList<>();
		List<ItSystem> itSystems = itSystemService.findAllForAttestation();
		for (ItSystem itSystem : itSystems) {
			List<Attestation> roleAttestationsForSystem = attestationDao.findItSystemRoleAttestations(itSystem.getId(), from, to);
			Attestation relevantAttestation = findRelevantAttestation(roleAttestationsForSystem);
			// the responsible users are the same for every role in the system, so only look them up once
			List<String> responsibleUserNames = relevantAttestation != null
				? getResponsibleUserNames(relevantAttestation)
				: Collections.emptyList();
			List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
			for (UserRole userRole : userRoles) {
				ITSystemRoleBuildAttestationDTO dto = new ITSystemRoleBuildAttestationDTO();
				dto.setItSystemName(itSystem.getName());
				dto.setRole(userRole.getName());
				dto.setSystemRole(userRole.getSystemRoleAssignments().stream().map(s -> s.getSystemRole().getName()).collect(Collectors.toList()));
				// it-systems without an attestation in the period still belong in the report, so make sure every row
				// is fully populated - the xls view does not tolerate nulls here.
				dto.setResponsibleUserNames(responsibleUserNames);
				dto.setAttestationStatus(AttestationStatus.NOT_VERIFIED);

				if (relevantAttestation != null) {
					ItSystemRoleAttestationEntry entryForRole = relevantAttestation.getItSystemUserRoleAttestationEntries()
						.stream().filter(i -> Objects.equals(i.getUserRoleId(), userRole.getId()))
						.findAny().orElse(null);

					dto.setAttestationStatus(getAttestationStatus(entryForRole));

					if (entryForRole != null) {
						dto.setAttestationDate(LocalDate.from(entryForRole.getCreatedAt()));
						dto.setPerformedBy(entryForRole.getPerformedByUserId());
						dto.setRemark(entryForRole.getRemarks());
					}
				}

				result.add(dto);
			}
		}
		return result;
	}

	private @NonNull List<String> getResponsibleUserNames(final Attestation attestation) {
		final Long responsibleCollectionId = attestation.getResponsibleCollectionId();
		if (responsibleCollectionId == null) {
			return Collections.emptyList();
		}
		return attestationResponsibleCollectionDao.findById(responsibleCollectionId)
			.map(AttestationResponsibleCollection::getUsersUuid)
			.map(uuids -> uuids.stream().map(cachedUserService::userNameFromUuidCached).toList())
			.orElseGet(Collections::emptyList);
	}

	private AttestationStatus getAttestationStatus(ItSystemRoleAttestationEntry entryForRole) {
		if (entryForRole == null) {
			return AttestationStatus.NOT_VERIFIED;
		}

		return entryForRole.getRemarks() != null
			? AttestationStatus.REMARKS
			: AttestationStatus.APPROVED;
	}

	private Attestation findRelevantAttestation(List<Attestation> attestations) {
		if (attestations == null || attestations.isEmpty()) {
			return null;
		}

		// If only one attestation, return it
		if (attestations.size() == 1) {
			return attestations.get(0);
		}

		// Multiple attestations: find newest verified, or fallback to newest unverified
		return attestations.stream()
			.filter(a -> a.getVerifiedAt() != null)
			.max(Comparator.comparing(Attestation::getVerifiedAt))
			.orElseGet(() -> attestations.stream()
				.max(Comparator.comparing(Attestation::getCreatedAt))
				.orElse(null));
	}


	private @NonNull List<String> getResponsibleUsers(AttestationUserRoleAssignmentDto assignment) {
		return assignment.getResponsibleCollectionId() != null
			? attestationResponsibleCollectionDao.findById(assignment.getResponsibleCollectionId())
			  .map(c -> c.getUsersUuid().stream()
						.map(u -> cachedUserService.userNameFromUuidCached(u)).toList()
			  ).orElse(Collections.emptyList())
			: Collections.emptyList();
	}


}
