package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.AttestationCachedItSystemService;
import dk.digitalidentity.rc.attestation.service.HistoricAssignmentAttestationService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.history.HistoryAttestationManagerDelegateDao;
import dk.digitalidentity.rc.dao.history.model.HistoryAttestationManagerDelegate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class UserAttestationTrackerService {
	@Autowired
	private RoleCatalogueConfiguration configuration;
	@Autowired
	private AttestationRunTrackerService runTrackerService;
	@Autowired
	private HistoricAssignmentAttestationService historicAssignmentService;
	@Autowired
	private AttestationOuAssignmentsDao ouAssignmentsDao;
	@Autowired
	private SettingsService settingsService;
	@Autowired
	private AttestationDao attestationDao;
	@Autowired
	private AttestationUserDao attestationUserDao;
	@Autowired
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;
	@Autowired
	private OrgUnitDao orgUnitDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private AttestationCachedItSystemService cachedItSystemService;

	@PersistenceContext
	private EntityManager entityManager;
	@Autowired
	private HistoryAttestationManagerDelegateDao historyAttestationManagerDelegateDao;
	@Autowired
	private OrgUnitService orgUnitService;
	@Autowired
	private UserService userService;

	@Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
	public void updateSystemUserAttestations(final LocalDate when) {
		cnt = cntUA = cntOu = ouAdCnt = 0;
		entityManager.setFlushMode(FlushModeType.COMMIT);
		runTrackerService.getAttestationRunToMaintain(when).ifPresent(run -> {
			// Attestationer for tomme collections er usynlige for alle (synlighed afgøres af
			// collection-medlemskab) og kan aldrig afsluttes — spring dem over.
			final Map<Long, Boolean> collectionHasResponsibles = new HashMap<>();
			historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when).stream()
					.filter(a -> collectionHasResponsibles.computeIfAbsent(a.getResponsibleCollectionId(), this::hasResponsibles))
					.forEach(a -> ensureWeHaveSystemUsersAttestationFor(run, a, when));

			// Ensure we have attestations for all direct OU assignments handled by an IT-system responsible.
			// HistoricAssignment replaces the former ouAssignmentsDao query here.
			historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when).stream()
					.filter(a -> collectionHasResponsibles.computeIfAbsent(a.getResponsibleCollectionId(), this::hasResponsibles))
					.forEach(a -> ensureWeHaveSystemUsersAttestationFor(run, a, when));
		});
	}

	private boolean hasResponsibles(final Long collectionId) {
		return attestationResponsibleCollectionDao.findById(collectionId)
				.map(c -> !c.getUsersUuid().isEmpty())
				.orElse(false);
	}

	@Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
	public void updateOrganisationUserAttestations(final LocalDate when) {
		cnt = cntUA = cntOu = 0;
		entityManager.setFlushMode(FlushModeType.COMMIT);
		runTrackerService.getAttestationRunToMaintain(when).ifPresent(run -> {
			// We start by deleting all attestation users
			// Reason for this is that the org hierarchy changes over time, so it's easier to just start from scratch every time.
			attestationDao.findByAttestationTypeAndAttestationRun(Attestation.AttestationType.ORGANISATION_ATTESTATION, run)
					.forEach(a -> {
						attestationUserDao.deleteAll(a.getUsersForAttestation());
						a.setUsersForAttestation(Collections.emptySet());
					});
			entityManager.flush();
			final OrgUnitFilter orgUnitFilter;
			if (settingsService.isAttestationOrgUnitSelectionOptInCached()) {
				orgUnitFilter = new OrgUnitFilter(true, settingsService.getScheduledAttestationOptedInOrgUnits(), Set.of());
			} else {
				orgUnitFilter = new OrgUnitFilter(false, Set.of(), findExemptedOUs());
			}

			List<HistoryAttestationManagerDelegate> managerDelegates = historyAttestationManagerDelegateDao.findAllByDate(when);
			List<String> delegatedManagerUuids = managerDelegates.stream().map(HistoryAttestationManagerDelegate::getManagerUuid).toList();

			//construct map of orgunits containing delegated managers, for easier lookup
			Map<String, Set<String>> managersForEachDelegatedOrgUnit = new HashMap<>();
			for (var managerDelegate : managerDelegates) {
				Optional<User> manager = userService.getOptionalByUuid(managerDelegate.getManagerUuid());
				if (manager.isPresent()) {
					List<OrgUnit> orgUnits = orgUnitService.getOrgUnitsForUser(manager.get());
					orgUnits.stream()
							.map(OrgUnit::getUuid)
							.forEach(ouUuid -> managersForEachDelegatedOrgUnit
									.computeIfAbsent(ouUuid, _ -> new HashSet<>())
									.add(manager.get().getUuid()));
				}
			}

			// Org unit roles assigned on the unit itself. The OU updater leaves responsible_ou_uuid NULL on rows
			// inherited from a parent, so this only yields units with something of their own to attest. The query
			// returns one representative row per unit, so exempt IT systems must be filtered inside it, not here.
			ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when).stream()
				.filter(a -> orgUnitFilter.includes(a.getResponsibleOuUuid()))
					.forEach(a -> {
						Set<String> delegatedManagersUuid = managersForEachDelegatedOrgUnit.get(a.getResponsibleOuUuid());
						boolean delegated = delegatedManagersUuid != null && !delegatedManagersUuid.isEmpty();
						ensureOrganisationAttestationFor(run, a, delegated ? Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION : Attestation.AttestationType.ORGANISATION_ATTESTATION, when);
					});

			// User assignments the unit's manager attests. For user assignments responsible_ou is the user's position
			// unit even for roles inherited from above, so the rows must be filtered by the same rule the closing side
			// uses. The query groups by assigned_through_type, so filtering rows here cannot drop a unit by accident.
			historicAssignmentService.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when).stream()
					// DO not create attestation for OUs that have been exempt in settings
					.filter(a -> orgUnitFilter.includes(a.getResponsibleOuUuid()))
					.filter(AttestationUserRoleAssignment::isAttestedByOuManager)
					.filter(a -> !cachedItSystemService.isItSystemExempt(a.getItSystemId()))
					.forEach(a -> {
						boolean delegated = delegatedManagerUuids.contains(a.getUserUuid());
						ensureOrganisationAttestationFor(run, a, delegated ? Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION : Attestation.AttestationType.ORGANISATION_ATTESTATION, when);
					});
			if (settingsService.isADAttestationEnabled() && !run.isSensitive() && !run.isExtraSensitive()) {
				// If AD attestation is enabled we want to make sure all org units with employees have attestations
				Map<String, Set<User>> uuidToUsers =
						userDao.findByDeletedFalse().stream()
								.filter(u -> !u.isDisabled())
								.flatMap(u -> {
									Stream<String> uuids;
									if (u.getPositions().stream().anyMatch(Position::isPrimary)) {
										uuids = u.getPositions().stream()
												.filter(Position::isPrimary)
												.map(Position::getOrgUnit)
												.filter(Objects::nonNull)
												.map(OrgUnit::getUuid);
									} else {
										uuids = u.getPositions().stream()
												.map(Position::getOrgUnit)
												.filter(Objects::nonNull)
												.map(OrgUnit::getUuid);
									}
									return uuids
										// If this is a manager we need to change to a parent OU with a different manager
										.map(uuid -> findTargetOuForAttestation(u, uuid))
										.map(uuid -> Map.entry(uuid, u));
								})
								.filter(entry -> orgUnitFilter.includes(entry.getKey()))
								.collect(Collectors.groupingBy(
										Map.Entry::getKey,
										Collectors.mapping(Map.Entry::getValue, Collectors.toSet())
								));

				uuidToUsers.forEach((ouUuid, users) -> {
					ensureWeHaveOrganisationAttestationFor(run, ouUuid, when, users);
				});
			}
		});

	}

	/**
	 * Determines the target organizational unit (OU) for attestation based on the
	 * specified user and the UUID of a position's organizational unit.
	 * If the user is the manager of the specified organizational unit, the method searches
	 * for a parent organizational unit with a different manager. If found, the UUID
	 * of that parent organizational unit is returned. Otherwise, the UUID of the original
	 * organizational unit is returned.
	 */
	private String findTargetOuForAttestation(final User user, final String positionOuUuid) {
		final OrgUnit orgUnit = orgUnitService.getByUuid(positionOuUuid);
		if (orgUnit != null && orgUnit.getManager() != null && orgUnit.getManager().getUuid().equals(user.getUuid())) {
			return orgUnitService.findParentOrgUnitWithDifferentManager(orgUnit, user.getUuid()).map(OrgUnit::getUuid)
				.orElse(positionOuUuid);
		}
		return positionOuUuid;
	}

	private record OrgUnitFilter(boolean optIn, Set<String> optedInOus, Set<String> exemptedOus) {
		boolean includes(final String orgUnitUuid) {
			if (optIn) {
				return optedInOus.contains(orgUnitUuid);
			}
			return !exemptedOus.contains(orgUnitUuid);
		}
	}

	private Set<String> findExemptedOUs() {
		return settingsService.getScheduledAttestationFilter().stream()
				.map(uuid -> orgUnitDao.findById(uuid))
				.filter(Optional::isPresent)
				.flatMap(ou -> getChildrenRecursive(ou.get(), 0))
				.map(OrgUnit::getUuid)
				.collect(Collectors.toSet());
	}

	private Stream<OrgUnit> getChildrenRecursive(final OrgUnit orgUnit, int level) {
		if (level > 15) {
			log.warn("Recursive loop detected reached level ${}, current ou: ${}", level, orgUnit.getUuid());
			return Stream.empty();
		}
		return Stream.concat(Stream.of(orgUnit), orgUnit.getChildren().stream()
				.flatMap(ou -> getChildrenRecursive(ou, level + 1)));
	}

	private boolean shouldDisregardAssignment(final AttestationRun run, boolean assignmentSensitive, boolean assignmentSuperSensitive) {
		if (run.isSensitive() && (!assignmentSensitive && !assignmentSuperSensitive)) {
			return true;
		}
		if (run.isExtraSensitive() && !assignmentSuperSensitive) {
			return true;
		}
		return false;
	}

	static int cntUA = 0;

	private void ensureWeHaveSystemUsersAttestationFor(final AttestationRun run,
													   final AttestationUserRoleAssignment assignment,
													   final LocalDate when) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		Attestation attestation = findSystemUsersAttestationFor(run, assignment).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation section yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation for this (system × responsible) pair
				attestation = createItSystemUsersAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		}
		addUser(attestation, assignment);
		if (cntUA++ % 100 == 0) {
			log.info("Attestation system user " + attestation.getUuid() + " found, progress count=" + cntUA);
		}
	}

	static int cntOu = 0;

	private void ensureOrganisationAttestationFor(final AttestationRun run,
												  final AttestationOuRoleAssignment assignment,
												  final Attestation.AttestationType type,
												  final LocalDate when) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		findOrCreateOrganisationAttestation(run, type, assignment.getResponsibleOuUuid(), assignment.getResponsibleOuName(), when)
				.ifPresent(attestation -> {
					if (cntOu++ % 100 == 0) {
						log.info("Attestation for organisation role " + attestation.getUuid() + " found, progress count=" + cntOu);
					}
				});
	}

	static int cnt = 0;

	private void ensureOrganisationAttestationFor(final AttestationRun run,
												  final AttestationUserRoleAssignment assignment,
												  final Attestation.AttestationType type,
												  final LocalDate when) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		findOrCreateOrganisationAttestation(run, type, assignment.getResponsibleOuUuid(), assignment.getResponsibleOuName(), when)
				.ifPresent(attestation -> {
					addUser(attestation, assignment);
					if (cnt++ % 100 == 0) {
						log.info("Attestation for organisation user role " + attestation.getUuid() + " found, progress count=" + cnt);
					}
				});
	}

	/** Empty while the deadline is still outside the attestation window - no attestation is created yet. */
	private Optional<Attestation> findOrCreateOrganisationAttestation(final AttestationRun run,
																	  final Attestation.AttestationType type,
																	  final String responsibleOuUuid,
																	  final String responsibleOuName,
																	  final LocalDate when) {
		final Optional<Attestation> existing = findOrganisationAttestationFor(run, type, responsibleOuUuid);
		if (existing.isPresent()) {
			return existing;
		}
		if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
			return Optional.empty();
		}
		return Optional.of(createOrganisationAttestationFor(run, type, responsibleOuUuid, responsibleOuName, run.isSensitive(), when, run.getDeadline()));
	}

	static int ouAdCnt = 0;

	private void ensureWeHaveOrganisationAttestationFor(final AttestationRun run,
														final String responsibleOuUuid,
														final LocalDate when, Set<User> users) {
		if (run.isSensitive() || run.isExtraSensitive()) {
			// This method creates a OU attestations even though there is no role assignment, this is
			// used when AD attestation is enabled an all OUs should be attestated.
			// So we NEVER wants this to happen for sensitive runs.
			return;
		}
		Attestation attestation = findOrganisationAttestationFor(run, Attestation.AttestationType.ORGANISATION_ATTESTATION, responsibleOuUuid).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation section yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				final Optional<OrgUnit> optOu = orgUnitDao.findById(responsibleOuUuid);
				if (optOu.isPresent()) {
					attestation = createOrganisationAttestationFor(run, Attestation.AttestationType.ORGANISATION_ATTESTATION, responsibleOuUuid, optOu.get().getName(), false, when, run.getDeadline());
				}
			}
		}
		if (ouAdCnt++ % 100 == 0) {
			log.info("Attestation for organisation " + attestation.getUuid() + " found, progress count=" + ouAdCnt);
		}

		// Create UserAttestations and bind them to the attestation object
		List<AttestationUser> activeDirectoryAttestations = new ArrayList<>();
		final Attestation finalAttestation = attestation;
		users.forEach(user -> {
			AttestationUser byUserUuid = attestationUserDao.findByUserUuidAndAttestation(user.getUuid(), finalAttestation);
			if (byUserUuid == null) {
				AttestationUser attestationUser = new AttestationUser();
				attestationUser.setAttestation(finalAttestation);
				attestationUser.setUserUuid(user.getUuid());
				attestationUser.setSensitiveRoles(false);
				activeDirectoryAttestations.add(attestationUser);
			}
		});
        if (!activeDirectoryAttestations.isEmpty()) {
			attestationUserDao.saveAll(activeDirectoryAttestations);
			log.debug("Created {} active directory attestations for attestation {}", activeDirectoryAttestations.size(), attestation.getUuid());
        }
	}

	private Optional<Attestation> findSystemUsersAttestationFor(final AttestationRun run, final AttestationUserRoleAssignment assignment) {
		return attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdAndAttestationRunOrderByIdAsc(
				Attestation.AttestationType.IT_SYSTEM_ATTESTATION, assignment.getItSystemId(), assignment.getResponsibleCollectionId(), run);
	}

	private Optional<Attestation> findOrganisationAttestationFor(final AttestationRun run, final Attestation.AttestationType type, final String ouUuid) {
		return attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidAndAttestationRunOrderByIdAsc(type, ouUuid, run);
	}

	private Attestation createOrganisationAttestationFor(final AttestationRun run,
														 final Attestation.AttestationType type,
														 final String responsibleOuUuid,
														 final String responsibleOuName,
														 boolean sensitive, final LocalDate when,
														 final LocalDate deadline) {
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(type)
				.sensitive(sensitive)
				.responsibleOuUuid(responsibleOuUuid)
				.responsibleOuName(responsibleOuName)
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);
	}

	private Attestation createItSystemUsersAttestationFor(final AttestationRun run,
														  final AttestationUserRoleAssignment assignment, boolean sensitive,
														  final LocalDate when, final LocalDate deadline) {
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
				.sensitive(sensitive)
				.itSystemId(assignment.getItSystemId())
				.itSystemName(assignment.getItSystemName())
				.responsibleCollectionId(assignment.getResponsibleCollectionId())
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);
	}



	private void addUser(final Attestation attestation, final AttestationUserRoleAssignment assignment) {
		final AttestationUser attestationUser = attestation.getUsersForAttestation().stream()
				.filter(a -> a.getUserUuid().equals(assignment.getUserUuid()))
				.findFirst()
				.orElseGet(() -> {
					final AttestationUser user = attestationUserDao.save(AttestationUser.builder()
							.userUuid(assignment.getUserUuid())
							.attestation(attestation)
							.sensitiveRoles(false)
							.build());
					final HashSet<AttestationUser> attestationUsers = new HashSet<>(attestation.getUsersForAttestation());
					attestationUsers.add(user);
					attestation.setUsersForAttestation(attestationUsers);
					return user;
				});
		if (assignment.isSensitiveRole() || assignment.isExtraSensitiveRole()) {
			attestationUser.setSensitiveRoles(true);
		}
	}

}
