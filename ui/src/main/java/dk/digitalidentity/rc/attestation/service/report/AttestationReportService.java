package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	
	@PersistenceContext
	private EntityManager entityManager;

	private static class VerificationInformationDTO {
		private AttestationStatus status = AttestationStatus.NOT_VERIFIED;
		private LocalDate verifiedAt;
		private String verifiedByName;
		private String verifiedByUserId;
		private String remark;
	}


	@Transactional
	public Map<String, Object> getOrgUnitReportModel(OrgUnit orgUnit, Locale locale, LocalDate since) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getOrgUnitRowIds(since, orgUnit), since, rowFilterService.filter()));
		model.put("orgUnitName", orgUnit.getName());
		model.put("from", since);
		model.put("to", LocalDate.now());
		model.put("messageSource", messageSource);
		model.put("locale", locale);
		return model;
	}

	@Transactional
	public Map<String, Object> getAllReportModel(Locale locale, LocalDate since) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which takes up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getAllRowIds(since), since, rowFilterService.filter()));
		model.put("orgUnitName", "Alle");
		model.put("from", since);
		model.put("to", LocalDate.now());
		model.put("messageSource", messageSource);
		model.put("locale", locale);
		return model;
	}

	@Transactional
	public Map<String, Object> getItSystemReportModel(ItSystem itSystem, Locale locale, LocalDate since) {
		// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
		// we don't need to flush all the time so just do it on commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);
		Map<String, Object> model = new HashMap<>();
		model.put("rowPaginator", new AttestationReportPaginator(this, getItSystemRowIds(since, itSystem), since, rowFilterService.filter()));
		model.put("itSystemName", itSystem.getName());
		model.put("from", since);
		model.put("to", LocalDate.now());
		model.put("messageSource", messageSource);
		model.put("locale", locale);

		return model;
	}

	private List<Long> getAllRowIds(final LocalDate since) {
		final LocalDate now = LocalDate.now();
		final LocalDate from = since != null ? since : now.minusYears(1);
		final LocalDate to = now.plusDays(1);
		return attestationUserRoleAssignmentDao.listAssignmentIdsValidBetween(from, to);
	}

	private List<Long> getOrgUnitRowIds(final LocalDate since, final OrgUnit orgUnit) {
		final LocalDate now = LocalDate.now();
		final LocalDate from = since != null ? since : now.minusYears(1);
		final LocalDate to = now.plusDays(1);
		return attestationUserRoleAssignmentDao.listAssignmentIdsValidBetweenForRoleOu(orgUnit.getUuid(), from, to);

	}

	public List<Long> getItSystemRowIds(final LocalDate since, final ItSystem itSystem) {
		final LocalDate now = LocalDate.now();
		final LocalDate from = since != null ? since : now.minusYears(1);
		final LocalDate to = now.plusDays(1);
		return attestationUserRoleAssignmentDao.listAssignmentValidBetweenForItSystem(itSystem.getId(), from, to);
	}

	@Transactional
	public List<RoleAssignmentReportRowDTO> getUserRoleRows(final LocalDate since, List<Long> rowIds) {
		final LocalDate now = LocalDate.now();
		final ZonedDateTime from = since != null ? since.atStartOfDay(CET_ZONE_ID) : now.minusYears(1).atStartOfDay(CET_ZONE_ID);
		final ZonedDateTime to = now.plusDays(1).atStartOfDay(CET_ZONE_ID);
		final List<List<Long>> partitionedRowIds = ListUtils.partition(rowIds, 1000);

		final AttestationReportContextService.AttestationReportContext context = attestationReportContextService.createContext(from.toLocalDate());

		return partitionedRowIds.stream()
				.flatMap(page -> attestationUserRoleAssignmentDao.findByIdIn(page).stream())
				.map(a -> {
					final VerificationInformationDTO verificationInformation = findVerificationInformation(a, context, from, to);
					return toRow(a, verificationInformation, now);
				})
				.collect(Collectors.toList());
	}

	private RoleAssignmentReportRowDTO toRow(final AttestationUserRoleAssignmentDto assignment, final VerificationInformationDTO verificationInformation, final LocalDate now) {
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
				.remark(verificationInformation.remark)
				.userName(assignment.getUserName())
				.userUserId(assignment.getUserId())
				.position(cachedUserService.getUserPositionsCached(assignment.getUserUuid(), assignment.getRoleOuUuid()))
				.status(assignment.getValidTo() == null || assignment.getValidTo().isAfter(now)
						? RoleStatus.ACTIVE
						: RoleStatus.INACTIVE)
				.orgUnit(assignment.getRoleOuName())
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
	private VerificationInformationDTO findVerificationInformation(final AttestationUserRoleAssignmentDto assignment,
																   final AttestationReportContextService.AttestationReportContext context,
																   final ZonedDateTime from, final ZonedDateTime to) {
		VerificationInformationDTO verificationInformation = new VerificationInformationDTO();
		final ZonedDateTime assignmentValidFrom = assignment.getValidFrom().atStartOfDay().atZone(CET_ZONE_ID);
		if (assignment.getResponsibleUserUuid() != null) {
			verificationInformation = context.getItSystemUserAttestations().stream()
					.filter(a -> Objects.equals(a.getItSystemId(), assignment.getItSystemId()))
					.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom))
					.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
					.map(attestation -> findVerificationInformationForSystemUserAttestation(assignment, attestation))
					.orElse(verificationInformation);
		}
		if (assignment.getResponsibleOuUuid() != null && verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
			if (assignment.getAssignedThroughUuid() == null || assignment.getAssignedThroughUuid().equals(assignment.getResponsibleOuUuid())) {
				// Same OU so lookup the org user entry
				verificationInformation = context.getOrganisationRolesAttestations().stream()
						.filter(a -> Objects.equals(a.getResponsibleOuUuid(), assignment.getResponsibleOuUuid()))
						.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom))
						.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
						.map(attestation -> findVerificationInformationForOUAttestation(assignment, attestation))
						.orElse(verificationInformation);
			}
			if (verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
				// OU Is elsewhere this entry must be inherited so lookup the org role entry
				verificationInformation = context.getOrganisationRolesAttestations().stream()
						.filter(a -> Objects.equals(a.getResponsibleOuUuid(), assignment.getAssignedThroughUuid()))
						.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom))
						.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
						.map(this::findVerificationInformationForInheritedOUAttestation)
						.orElse(verificationInformation);
			}
		}
		if (assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT && verificationInformation.status == AttestationStatus.NOT_VERIFIED) {
			// Se if an it-system responsible verified a parent ou
			verificationInformation = context.getItSystemUserAttestations().stream()
					.filter(a -> Objects.equals(a.getItSystemId(), assignment.getItSystemId()) && a.getResponsibleUserUuid() != null)
					.filter(a -> a.getVerifiedAt().isAfter(assignmentValidFrom))
					.max(Comparator.comparing(Attestation::getCreatedAt)) // Find the newest it-system user attestation for the given it-system
					.map(attestation -> findVerificationInformationForSystemOuAttestation(assignment, attestation))
					.orElse(verificationInformation);
		}
		return verificationInformation;
	}

	private VerificationInformationDTO findVerificationInformationForInheritedOUAttestation(final Attestation attestation) {
		final VerificationInformationDTO result = new VerificationInformationDTO();
		if (attestation != null && attestation.getOrganisationRolesAttestationEntry() != null) {
			final OrganisationRoleAttestationEntry entry = attestation.getOrganisationRolesAttestationEntry();
			if (entry.getRemarks() != null) {
				result.status = AttestationStatus.REMARKS;
			} else {
				result.status = AttestationStatus.APPROVED;
			}
			result.remark = entry.getRemarks();
			result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
			result.verifiedByUserId = entry.getPerformedByUserId();
			result.verifiedAt = entry.getCreatedAt().toLocalDate();
		}
		return result;
	}

	private VerificationInformationDTO findVerificationInformationForOUAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		final VerificationInformationDTO result = new VerificationInformationDTO();
		final OrganisationUserAttestationEntry entry = lookupOrganisationUserEntry(attestation, assignment);
		if (entry != null) {
			if (entry.getRemarks() != null) {
				result.status = AttestationStatus.REMARKS;
			} else if (entry.isAdRemoval()) {
				result.status = AttestationStatus.DELETE;
			} else {
				result.status = AttestationStatus.APPROVED;
			}
			result.remark = entry.getRemarks();
			result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
			result.verifiedByUserId = entry.getPerformedByUserId();
			result.verifiedAt = entry.getCreatedAt().toLocalDate();
		}
		return result;
	}

	private VerificationInformationDTO findVerificationInformationForSystemUserAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		VerificationInformationDTO result = new VerificationInformationDTO();
		final ItSystemUserAttestationEntry entry = lookupItSystemUserEntry(attestation, assignment);
		if (entry != null) {
			result.status = entry.getRemarks() != null
					? AttestationStatus.REMARKS
					: AttestationStatus.APPROVED;
			result.remark = entry.getRemarks();
			result.verifiedByName = cachedUserService.userNameFromUuidCached(entry.getPerformedByUserUuid());
			result.verifiedByUserId = entry.getPerformedByUserId();
			result.verifiedAt = entry.getCreatedAt().toLocalDate();
		}
		return result;
	}

	private VerificationInformationDTO findVerificationInformationForSystemOuAttestation(final AttestationUserRoleAssignmentDto assignment, final Attestation attestation) {
		VerificationInformationDTO result = new VerificationInformationDTO();
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
		return result;
	}


	@SafeVarargs
	private static Attestation findNewestAttestation(final List<Attestation> ... attestations) {
		return Stream.of(attestations)
				.flatMap(Collection::stream)
				.filter(a -> a.getDeadline() != null)
				.max(Comparator.comparing(Attestation::getDeadline))
				.orElse(null);
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

}
