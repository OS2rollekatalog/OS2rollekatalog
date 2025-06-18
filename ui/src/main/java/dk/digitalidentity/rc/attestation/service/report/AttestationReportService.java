package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.ADAttestationUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CET_ZONE_ID;

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
	
	@PersistenceContext
	private EntityManager entityManager;

	private static class VerificationAndAttestationInformationDTO {
		private AttestationStatus status = AttestationStatus.NOT_VERIFIED;
		private LocalDate verifiedAt;
		private String verifiedByName;
		private String verifiedByUserId;
		private String remark;
		private LocalDate attestationCreatedAt;
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

	public Map<String, Object> getAuditReportModel(Locale locale, LocalDate since, LocalDate to, AttestationRun attestationRun) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which takes up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getAllRowIds(since, to), since, to, rowFilterService.filter()));
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

	@Transactional
	public List<RoleAssignmentReportRowDTO> getUserRoleRows(final LocalDate since, final LocalDate when, List<Long> rowIds) {
		final ZonedDateTime from = since != null ? since.atStartOfDay(CET_ZONE_ID) : when.minusYears(1).atStartOfDay(CET_ZONE_ID);
		final ZonedDateTime to = when.plusDays(1).atStartOfDay(CET_ZONE_ID);
		final List<List<Long>> partitionedRowIds = ListUtils.partition(rowIds, 1000);

		final AttestationReportContextService.AttestationReportContext context = attestationReportContextService.createContext(from.toLocalDate());

		return partitionedRowIds.stream()
				.flatMap(page -> attestationUserRoleAssignmentDao.findByIdIn(page).stream())
				.map(a -> {
					final VerificationAndAttestationInformationDTO verificationInformation = findVerificationAndAttestationInformation(a, context, from, to);
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
				.inherited(assignment.isInherited())
				.responsibleOu(assignment.getResponsibleOuName())
				.responsibleUser(StringUtils.isNotEmpty(assignment.getResponsibleUserUuid())
						? cachedUserService.userNameFromUuidCached(assignment.getResponsibleUserUuid())
						: null)
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
				.position(cachedUserService.getUserPositionsCached(assignment.getUserUuid(), assignment.getRoleOuUuid()))
				.status(assignment.getValidTo() == null || assignment.getValidTo().isAfter(now)
						? RoleStatus.ACTIVE
						: RoleStatus.INACTIVE)
				.orgUnit(assignment.getRoleOuName())
				.attestationCreatedAt(verificationInformation.attestationCreatedAt)
				.validTo(assignment.getValidTo())
				.originallyAssignedFrom(assignment.getAssignedFrom())
				.build();
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
																   final ZonedDateTime from, final ZonedDateTime to) {
		VerificationAndAttestationInformationDTO verificationInformation = new VerificationAndAttestationInformationDTO();
		final ZonedDateTime assignmentValidFrom = assignment.getValidFrom().atStartOfDay().atZone(CET_ZONE_ID);
		if (assignment.getResponsibleUserUuid() != null) {
			verificationInformation = context.getItSystemUserAttestations().stream()
					.filter(a -> Objects.equals(a.getItSystemId(), assignment.getItSystemId()))
					.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom) && a.getVerifiedAt().isBefore(to))
					.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
					.map(attestation -> findVerificationInformationForSystemUserAttestation(assignment, attestation))
					.orElse(verificationInformation);
		}
		if (assignment.getResponsibleOuUuid() != null && verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
			if (assignment.getAssignedThroughUuid() == null || assignment.getAssignedThroughUuid().equals(assignment.getResponsibleOuUuid())) {
				// Same OU so lookup the org user entry
				verificationInformation = context.getOrganisationRolesAttestations().stream()
						.filter(a -> Objects.equals(a.getResponsibleOuUuid(), assignment.getResponsibleOuUuid()))
						.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom)  && a.getVerifiedAt().isBefore(to))
						.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
						.map(attestation -> findVerificationInformationForOUAttestation(assignment, attestation))
						.orElse(verificationInformation);
			}
			if (verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
				// OU Is elsewhere this entry must be inherited so lookup the org role entry
				verificationInformation = context.getOrganisationRolesAttestations().stream()
						.filter(a -> Objects.equals(a.getResponsibleOuUuid(), assignment.getAssignedThroughUuid()))
						.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom) && a.getVerifiedAt().isBefore(to))
						.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
						.map(this::findVerificationInformationForInheritedOUAttestation)
						.orElse(verificationInformation);
			}
		}
		if (assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT && verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
			// Se if an it-system responsible verified a parent ou
			verificationInformation = context.getItSystemUserAttestations().stream()
					.filter(a -> Objects.equals(a.getItSystemId(), assignment.getItSystemId()) && a.getResponsibleUserUuid() != null)
					.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom) && a.getVerifiedAt().isBefore(to))
					.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
					.map(attestation -> findVerificationInformationForSystemOuAttestation(assignment, attestation))
					.orElse(verificationInformation);
		}
		return verificationInformation;
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

			result.attestationCreatedAt = attestation.getCreatedAt();
		}

		return result;
	}

	private VerificationAndAttestationInformationDTO findVerificationInformationForOUAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		final VerificationAndAttestationInformationDTO result = new VerificationAndAttestationInformationDTO();
		if (assignment.getAssignedThroughType().equals(AssignedThroughType.ORGUNIT)) {
			final OrganisationRoleAttestationEntry entry = lookupOrganisationEntry(attestation);
			if (entry != null) {
				result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
				result.remark = entry.getRemarks();
				result.verifiedByUserId = entry.getPerformedByUserId();
				result.verifiedAt = entry.getCreatedAt().toLocalDate();
				if (result.remark != null) {
					result.status = AttestationStatus.REMARKS;
				} else {
					result.status = AttestationStatus.APPROVED;
				}
			}
		} else {
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
		}

		if (attestation != null) {
			result.attestationCreatedAt = attestation.getCreatedAt();
		}

		return result;
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

		if (attestation != null) {
			result.attestationCreatedAt = attestation.getCreatedAt();
		}

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

		if (attestation != null) {
			result.attestationCreatedAt = attestation.getCreatedAt();
		}

		return result;
	}

	private List<ADAttestationUserDTO> getADUserAttestation(AttestationRun attestationRun) {
		List<ADAttestationUserDTO> result = new ArrayList<>();
		if (settingsService.isADAttestationEnabled()) {
			for (Attestation attestation : attestationRun.getAttestations()) {
				List<User> users = userService.getAllByUuidIn(attestation.getUsersForAttestation().stream().map(u -> u.getUserUuid()).collect(Collectors.toSet()));
				result.addAll(users.stream().map(u -> ADAttestationUserDTO.builder()
						.uuid(u.getUuid())
						.name(u.getName())
						.username(u.getUserId())
						.verifiedAt(attestation.getVerifiedAt() == null ? null : LocalDate.from(attestation.getVerifiedAt()))
						.responsibleOU(attestation.getResponsibleOuName())
						.responsibleUser(StringUtils.isNotEmpty(attestation.getResponsibleUserUuid())
								? cachedUserService.userNameFromUuidCached(attestation.getResponsibleUserUuid())
								: null)
						.build())
					.collect(Collectors.toList()));
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

	private static OrganisationRoleAttestationEntry lookupOrganisationEntry(final Attestation attestation) {
		return attestation.getOrganisationRolesAttestationEntry();
	}

}
