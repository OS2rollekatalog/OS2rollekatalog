package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.ManagerDelegateOrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitRoleGroupAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserAttestationDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;

import dk.digitalidentity.rc.dao.ManagerDelegateDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.projections.OrgUnitManagerUuid;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.service.OrganisationAttestationService.isOrgVerified;

@Slf4j
@Component
public class ManagerDelegateAttestationService {
	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private ManagerDelegateDao managerDelegateDao;

	@Autowired
	private OrganisationAttestationService organisationAttestationService;

	@Autowired
	private AttestationUserRoleAssignmentDao userRoleAssignmentDao;

	@Autowired
	private AttestationOuAssignmentsDao ouAssignmentsDao;

	@Autowired
	private AttestationDao attestationDao;

	@Autowired
	private UserService userService;
	@Autowired
	private OrgUnitDao orgUnitDao;

	@Autowired
	private OrgUnitService orgUnitService;

	@Transactional
	public OrganisationAttestationDTO getAttestationDTO(final Attestation attestation, final String currentUserUuid, final boolean undecidedUsersOnly) {
		// Delegates to shared implementation in organisationAttestationService - manager-delegate attestations use ORGANISATION_ATTESTATION history
		return organisationAttestationService.getAttestation(attestation, currentUserUuid, undecidedUsersOnly, Attestation.AttestationType.ORGANISATION_ATTESTATION);
	}

	public List<User> getManagedUsersForDelegate(User delegate) {
		return managerDelegateDao.findActiveByDelegate(delegate, LocalDate.now()).stream()
				.map(ManagerDelegate::getManager)
				.toList();
	}

	@Transactional
	public List<ManagerDelegateOrganisationAttestationDTO> listOrganisationsForAttestation(final AttestationRun run, final List<User> delegatedManagers) {
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final Set<String> delegatedManagerUuids = delegatedManagers.stream()
				.map(User::getUuid)
				.collect(Collectors.toSet());

		final List<Attestation> delegatedAttestations = run.getAttestations().stream()
				.filter(a -> a.getAttestationType() == Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION)
				.toList();

		// Batch-fetch only (ouUuid, managerUuid) pairs — avoids loading full OrgUnit entities into memory, and avoids n+1 lookup
		final Map<String, String> managerUuidByOuUuid = orgUnitDao
				.findByActiveTrueAndManagerNotNullAndUuidIn(delegatedAttestations.stream().map(Attestation::getResponsibleOuUuid).toList())
				.stream()
				.collect(Collectors.toMap(OrgUnitManagerUuid::getUuid, p -> p.getManager().getUuid()));

		return delegatedAttestations.stream()
				.filter(a -> delegatedManagerUuids.contains(managerUuidByOuUuid.get(a.getResponsibleOuUuid())))
				.map(a -> toShallowOrganisationDto(a.getCreatedAt(), a, delegatedManagers))
				.toList();
	}

	/**
	 * Convert to {@link OrganisationAttestationDTO} will only include user assignments, not organisation level assignments
	 */
	ManagerDelegateOrganisationAttestationDTO toShallowOrganisationDto(final LocalDate when, final Attestation attestationOrganisation, List<User> delegatedManagers) {
		List<String> delegatedManagerUuids = delegatedManagers.stream().map(User::getUuid).toList();

		final List<AttestationUserRoleAssignment> userRoleAssignments = userRoleAssignmentDao
				.listValidAssignmentsByResponsibleOu(when, attestationOrganisation.getResponsibleOuUuid());
		final List<AttestationOuRoleAssignment> organisationAssignments = ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(attestationOrganisation.getCreatedAt(), attestationOrganisation.getResponsibleOuUuid());
		final List<OrgUnitRoleGroupAssignmentDTO> orgUnitRoleGroupAssignments = organisationAttestationService.orgUnitRoleGroups(organisationAssignments);
		final List<OrgUnitUserRoleAssignmentItSystemDTO> orgUnitUserRoleAssignmentsPrItSystem = organisationAttestationService.orgUnitUserRolesPrItSystem(attestationOrganisation, organisationAssignments);
		final String performedBy = AttestationOverviewService.resolvePerformedBy(attestationOrganisation.getOrganisationUserAttestationEntries(), userService);
		ManagerDelegateOrganisationAttestationDTO managerDelegateOrganisationAttestationDTO = ManagerDelegateOrganisationAttestationDTO.builder()
				.createdAt(attestationOrganisation.getCreatedAt())
				.attestationUuid(attestationOrganisation.getUuid())
				.ouUuid(attestationOrganisation.getResponsibleOuUuid())
				.ouName(attestationOrganisation.getResponsibleOuName())
				.orgUnitRolesVerified(isOrgVerified(attestationOrganisation, orgUnitRoleGroupAssignments, orgUnitUserRoleAssignmentsPrItSystem))
				.deadLine(attestationOrganisation.getDeadline())
				.orgUnitRoleGroupAssignments(orgUnitRoleGroupAssignments)
				.orgUnitUserRoleAssignmentsPrItSystem(orgUnitUserRoleAssignmentsPrItSystem)
				.verifiedAt(attestationOrganisation.getVerifiedAt() != null ? attestationOrganisation.getVerifiedAt().toLocalDate() : null)
				.performedBy(performedBy)
				.userAttestations(organisationAttestationService.buildUserAttestations(userRoleAssignments, attestationOrganisation, false, when).stream()
						.filter(ua -> delegatedManagerUuids.contains(ua.getUserUuid()))
						.toList())
				.build();

		final Set<String> associatedManagerNames = managerDelegateOrganisationAttestationDTO.getUserAttestations().stream()
				.filter(ua -> delegatedManagerUuids.contains(ua.getUserUuid()))
				.map(UserAttestationDTO::getUserName).collect(Collectors.toSet());

		orgUnitService.getManagerName(attestationOrganisation.getResponsibleOuUuid())
				.ifPresent(name -> {

					if (delegatedManagers.stream().map(User::getName).toList().contains(name.getManager().getName())) {
						associatedManagerNames.add(name.getManager().getName());
					}
				});

		managerDelegateOrganisationAttestationDTO.setAssociatedManagerNames(associatedManagerNames);
		return managerDelegateOrganisationAttestationDTO;
	}

	public List<AttestationOverviewDTO> buildOrgUnitsOverviews(final List<ManagerDelegateOrganisationAttestationDTO> orgsForAttestation, User currentUser, boolean readOnly) {
		return orgsForAttestation.stream()
				.map(o -> buildOrgUnitOverview(o, readOnly, currentUser.getUuid()))
				.toList();
	}

	public AttestationOverviewDTO buildOrgUnitOverview(final ManagerDelegateOrganisationAttestationDTO organisationAttestationDto, boolean readOnly, String currentUserUuid) {
		if (!readOnly && currentUserUuid != null && organisationAttestationDto.getVerifiedAt() == null && organisationAttestationDto.getPerformedBy() == null) {
			List<UserAttestationDTO> remaining = organisationAttestationDto.getUserAttestations().stream()
					.filter(u -> u.getVerifiedByUserId() == null && u.getRemarks() == null && !u.isAdRemoval())
					.toList();
			if (!remaining.isEmpty() && remaining.stream().allMatch(u -> u.getUserUuid().equals(currentUserUuid))) {
				readOnly = true;
			}
		}
		return buildOrgUnitOverview(organisationAttestationDto, readOnly);
	}

	public AttestationOverviewDTO buildOrgUnitOverview(final ManagerDelegateOrganisationAttestationDTO organisationAttestationDto, boolean readOnly) {
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
				substitutes, orgsAttestated, orgsToAttestate, hasOrgAssignments ? 1 : 0, organisationAttestationDto.getAssociatedManagerNames().stream().toList(), organisationAttestationDto.getVerifiedAt(), organisationAttestationDto.getPerformedBy());
	}



}
