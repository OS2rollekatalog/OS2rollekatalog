package dk.digitalidentity.rc.attestation.service;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CET_ZONE_ID;
import static dk.digitalidentity.rc.attestation.AttestationConstants.REPORT_LOCK_NAME;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceContext;

import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationLock;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;

@Service
public class AttestationReportService {

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private AttestationDao attestationDao;
	
	@Autowired
	private AttestationUserRoleAssignmentDao attestationUserRoleAssignmentDao;
	
	@Autowired
	private AttestationCachedUserService cachedUserService;
	
	@Autowired
	private AttestationLockService lockService;
	
	@PersistenceContext
	private EntityManager entityManager;

	private static class VerificationInformationDTO {
		private AttestationStatus status = AttestationStatus.NOT_VERIFIED;
		private LocalDate verifiedAt;
		private String verifiedByName;
		private String verifiedByUserId;
		private String remark;
	}

	/**
	 * Ensure only one report can be generated at the time.
	 * Will acquire a row lock on an {@link AttestationLock}
	 */
	private <T> T lockedScope(Supplier<T> supplier) {
		try {
			lockService.acquireLock(REPORT_LOCK_NAME);
			return supplier.get();
		} finally {
			lockService.releaseLock(REPORT_LOCK_NAME);
		}
	}


	@Transactional
	public Map<String, Object> getOrgUnitReportModel(OrgUnit orgUnit, Locale locale, LocalDate since) {
		return lockedScope(() -> {
			// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
			// we don't need to flush all the time so just do it on commit.
			entityManager.setFlushMode(FlushModeType.COMMIT);
			Map<String, Object> model = new HashMap<>();
			model.put("rows", getUserRoleRows(since, getOrgUnitRowIds(since, orgUnit)));
			model.put("orgUnitName", orgUnit.getName());
			model.put("from", since);
			model.put("to", LocalDate.now());
			model.put("messageSource", messageSource);
			model.put("locale", locale);
			return model;
		});
	}

	@Transactional
	public Map<String, Object> getAllReportModel(Locale locale, LocalDate since) {
		return lockedScope(() -> {
			// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
			// we don't need to flush all the time so just do it on commit.
			entityManager.setFlushMode(FlushModeType.COMMIT);
			Map<String, Object> model = new HashMap<>();
			model.put("rows", getUserRoleRows(since, getAllRowIds(since)));
			model.put("orgUnitName", "Alle");
			model.put("from", since);
			model.put("to", LocalDate.now());
			model.put("messageSource", messageSource);
			model.put("locale", locale);
			return model;
		});
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

	private List<RoleAssignmentReportRowDTO> getUserRoleRows(final LocalDate since, List<Long> rowIds) {
		final LocalDate now = LocalDate.now();
		final LocalDate from = since != null ? since : now.minusYears(1);
		final LocalDate to = now.plusDays(1);
		final List<List<Long>> partitionedRowIds = ListUtils.partition(rowIds, 500);
		return partitionedRowIds.stream()
				.flatMap(page -> attestationUserRoleAssignmentDao.findByIdIn(page).stream())
				.map(a -> {
					entityManager.detach(a);
					final VerificationInformationDTO verificationInformation = findVerificationInformation(a, from.atStartOfDay(CET_ZONE_ID), to.atStartOfDay(CET_ZONE_ID));
					return toRow(a, verificationInformation, now);
				})
				.collect(Collectors.toList());
	}

	@Transactional
	public Map<String, Object> getItSystemReportModel(ItSystem itSystem, Locale locale, LocalDate since) {
		return lockedScope(() -> {
			// Default hibernate have FlushModeType.AUTO, this causes a flood of constant flushes which tages up a lot of time
			// we don't need to flush all the time so just do it on commit.
			entityManager.setFlushMode(FlushModeType.COMMIT);
			Map<String, Object> model = new HashMap<>();
			model.put("rows", getItSystemRoleAssignments(since, itSystem));
			model.put("itSystemName", itSystem.getName());
			model.put("from", since);
			model.put("to", LocalDate.now());
			model.put("messageSource", messageSource);
			model.put("locale", locale);

			return model;
		});
	}

	private List<RoleAssignmentReportRowDTO> getItSystemRoleAssignments(final LocalDate since, final ItSystem itSystem) {
		final LocalDate now = LocalDate.now();
		final LocalDate from = since != null ? since : now.minusYears(1);
		final LocalDate to = now.plusDays(1);
		try (final Stream<AttestationUserRoleAssignment> assignmentStream = attestationUserRoleAssignmentDao.streamAssignmentValidBetweenForItSystem(itSystem.getId(), from, to)) {
			return assignmentStream
					.map(a -> {
						final VerificationInformationDTO verificationInformation = findVerificationInformation(a, from.atStartOfDay(CET_ZONE_ID), to.atStartOfDay(CET_ZONE_ID));
						return toRow(a, verificationInformation, now);
					})
					.collect(Collectors.toList());
		}
	}

	private RoleAssignmentReportRowDTO toRow(final AttestationUserRoleAssignment assignment, final VerificationInformationDTO verificationInformation, final LocalDate now) {
		return RoleAssignmentReportRowDTO.builder()
				.itSystemName(assignment.getItSystemName())
				.assignedFrom(assignment.getValidFrom())
				.assignedTo(assignment.getValidTo())
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

	/**
	 * This "rather lovely" method will figure out which attestation the supplied assignment belongs to and calculate
	 * the needed verification information for a report.
	 */
	private VerificationInformationDTO findVerificationInformation(final AttestationUserRoleAssignment assignment,
																   final ZonedDateTime from, final ZonedDateTime to) {
		final VerificationInformationDTO verificationInformation = new VerificationInformationDTO();
		if (assignment.getResponsibleUserUuid() != null) {
			final Attestation attestation = findNewestAttestation(attestationDao.findItSystemUserAttestationsForUser(assignment.getItSystemId(), assignment.getUserUuid(), from, to));
			return findVerificationInformationForSystemUserAttestation(assignment, attestation);
		} else if (assignment.getResponsibleOuUuid() != null) {
			if (assignment.getAssignedThroughUuid() == null || assignment.getAssignedThroughUuid().equals(assignment.getResponsibleOuUuid())) {
				// Same OU so lookup the org user entry
				final Attestation attestation = findNewestAttestation(attestationDao.findOrganisationUserAttestationsForUser(assignment.getResponsibleOuUuid(), assignment.getUserUuid(), from, to));
				return findVerificationInformationForOUAttestation(assignment, attestation);
			} else {
				// OU Is elsewhere this entry must be inherited so lookup the org role entry
				final Attestation attestation = findNewestAttestation(attestationDao.findOrganisationRoleAttestationsForOU(assignment.getAssignedThroughUuid(), from, to));
				return findVerificationInformationForInheritedOUAttestation(attestation);
			}
		} else if (assignment.getAssignedThroughType() == AssignedThroughType.ORGUNIT) {
			// Se of an it-system responsible verified a parent ou
			List<Attestation> itSystemUserAttestations = attestationDao.findItSystemUserAttestations(assignment.getItSystemId(), from, to).stream()
					.filter(a -> a.getResponsibleUserUuid() != null)
					.collect(Collectors.toList());
			final Attestation attestation = findNewestAttestation(itSystemUserAttestations);
			return findVerificationInformationForSystemOuAttestation(assignment, attestation);
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

	private VerificationInformationDTO findVerificationInformationForOUAttestation(final AttestationUserRoleAssignment assignment, final Attestation attestation) {
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

	private VerificationInformationDTO findVerificationInformationForSystemUserAttestation(final AttestationUserRoleAssignment assignment, final Attestation attestation) {
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

	private VerificationInformationDTO findVerificationInformationForSystemOuAttestation(final AttestationUserRoleAssignment assignment, final Attestation attestation) {
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
	private Attestation findNewestAttestation(final List<Attestation> ... attestations) {
		return Stream.of(attestations)
				.flatMap(Collection::stream)
				.filter(a -> a.getDeadline() != null)
				.max(Comparator.comparing(Attestation::getDeadline))
				.orElse(null);
	}

	private static ItSystemOrganisationAttestationEntry lookupItSystemOuEntry(final Attestation attestation,
																			  final AttestationUserRoleAssignment assignment) {
		if (attestation == null || attestation.getItSystemOrganisationAttestationEntries().isEmpty()) {
			return null;
		}
		return attestation.getItSystemOrganisationAttestationEntries().stream()
				.filter(a -> Objects.equals(a.getOrganisationUuid(), assignment.getAssignedThroughUuid()))
				.findFirst()
				.orElse(null);
	}

	private static ItSystemUserAttestationEntry lookupItSystemUserEntry(final Attestation attestation,
			final AttestationUserRoleAssignment assignment) {
		if (attestation == null || attestation.getItSystemUserAttestationEntries().isEmpty()) {
			return null;
		}
		return attestation.getItSystemUserAttestationEntries().stream()
				.filter(a -> Objects.equals(a.getUserUuid(), assignment.getUserUuid()))
				.findFirst()
				.orElse(null);
	}

	private static OrganisationUserAttestationEntry lookupOrganisationUserEntry(final Attestation attestation,
																				final AttestationUserRoleAssignment assignment) {
		if (attestation == null || attestation.getOrganisationUserAttestationEntries().size() == 0) {
			return null;
		}
		return attestation.getOrganisationUserAttestationEntries().stream()
				.filter(a -> Objects.equals(a.getUserUuid(), assignment.getUserUuid()))
				.findFirst()
				.orElse(null);
	}

}
