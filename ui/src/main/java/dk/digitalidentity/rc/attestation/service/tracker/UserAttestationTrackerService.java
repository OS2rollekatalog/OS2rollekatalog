package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.AttestationCachedItSystemService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.history.HistoryAttestationManagerDelegateDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryAttestationManagerDelegate;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ManagerDelegateService;
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
	private AttestationUserRoleAssignmentDao userRoleAssignmentDao;
	@Autowired
	private AttestationOuAssignmentsDao ouAssignmentsDao;
	@Autowired
	private SettingsService settingsService;
	@Autowired
	private AttestationDao attestationDao;
	@Autowired
	private AttestationUserDao attestationUserDao;
	@Autowired
	private HistoryUserDao historyUserDao;
	@Autowired
	private OrgUnitDao orgUnitDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private AttestationCachedItSystemService cachedItSystemService;

	@PersistenceContext
	private EntityManager entityManager;
	@Autowired
	private ManagerDelegateService managerDelegateService;
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
		runTrackerService.getAttestationRunWithDeadlineNotAfter(when).ifPresent(run -> {
			userRoleAssignmentDao.findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem(when)
					.forEach(a -> ensureWeHaveSystemUsersAttestationFor(run, a, when));

			// Ensure we have attestations for all direct OU assignments
			// We should create attestation for all ou assignments that are not inherited.
			ouAssignmentsDao.findValidGroupByResponsibleUserUuidAndSensitiveRole(when)
					.forEach(a -> ensureWeHaveSystemUsersAttestationForOu(run, a, when));
		});
	}

	@Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
	public void updateOrganisationUserAttestations(final LocalDate when) {
		cnt = cntUA = cntOu = 0;
		entityManager.setFlushMode(FlushModeType.COMMIT);
		runTrackerService.getAttestationRunWithDeadlineNotAfter(when).ifPresent(run -> {
			// We start by deleting all attestation users
			// Reason for this is that the org hierarchy changes over time, so it's easier to just start from scratch every time.
			attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(Attestation.AttestationType.ORGANISATION_ATTESTATION, when)
					.forEach(a -> {
						attestationUserDao.deleteAll(a.getUsersForAttestation());
						a.setUsersForAttestation(Collections.emptySet());
					});
			entityManager.flush();
			final Set<String> exemptedOus;
			final Set<String> optedInOus;
			if (settingsService.isAttestationOrgUnitSelectionOptIn()) {
				optedInOus = settingsService.getScheduledAttestationOptedInOrgUnits();
				exemptedOus = new HashSet<>();
			} else {
				optedInOus = new HashSet<>();
				exemptedOus = findExemptedOUs();
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
							.forEach(ouUuid -> {
								if (!managersForEachDelegatedOrgUnit.containsKey(ouUuid)) {
									managersForEachDelegatedOrgUnit.put(ouUuid, Set.of(manager.get().getUuid()));
								} else {
									managersForEachDelegatedOrgUnit.get(ouUuid).add(manager.get().getUuid());
								}
							});
				}
			}

			// Ensure we have attestations for all direct assignments
			// We should create attestation for all ou assignments that are not inherited.
			ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when).stream()
					.filter(a -> shouldIncludeInAttestation(a.getResponsibleOuUuid(), exemptedOus, optedInOus))
				.filter(a -> !cachedItSystemService.isItSystemExempt(a.getItSystemId()))
				.forEach(a -> {
					Set<String> delegatedManagersUuid = managersForEachDelegatedOrgUnit.get(a.getResponsibleOuUuid());
					if (delegatedManagersUuid != null && !delegatedManagersUuid.isEmpty()) {
						ensureManagerDelegateAttestation(run, a, when);
					} else {
						ensureWeHaveOrganisationAttestationForOu(run, a, when);
					}
				}
			);

			userRoleAssignmentDao.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when).stream()
					// DO not create attestation for OUs that have been exempt in settings
					.filter(a -> shouldIncludeInAttestation(a.getResponsibleOuUuid(), exemptedOus, optedInOus))
					.filter(a -> !a.isInherited())
					.filter(a -> !cachedItSystemService.isItSystemExempt(a.getItSystemId()))
					.forEach(a ->
							{
								if (delegatedManagerUuids.contains(a.getUserUuid())) {
									ensureManagerDelegateAttestation(run, a, when);
								} else {
									ensureWeHaveOrganisationAttestationFor(run, a, when);
								}
							}
					);
			if (settingsService.isADAttestationEnabled() && !run.isSensitive() && !run.isExtraSensitive()) {
				// If AD attestation is enabled we want to make sure all org units with employees have attestations
				userDao.findByDeletedFalse().stream()
						.filter(u -> !u.isDisabled())
						.flatMap(u -> {
							//Filters out non-primary if at least one primary position is present
									if (u.getPositions().stream().anyMatch(Position::isPrimary)) {
										return u.getPositions().stream()
												.filter(Position::isPrimary)
												.map(Position::getOrgUnit)
												.filter(Objects::nonNull)
												.map(OrgUnit::getUuid);
									} else {
										return u.getPositions().stream()
												.map(Position::getOrgUnit)
												.filter(Objects::nonNull)
												.map(OrgUnit::getUuid);
									}
								}
						)
						.filter(uuid -> shouldIncludeInAttestation(uuid, exemptedOus, optedInOus))
						.distinct()
						.forEach(uuid -> ensureWeHaveOrganisationAttestationFor(run, uuid, when));
			}
		});

	}

	private boolean shouldIncludeInAttestation(String orgunitUuid, Set<String> excludedOrgunitUuids, Set<String> includedOrgunitUuids) {
		if (includedOrgunitUuids != null && !includedOrgunitUuids.isEmpty()) {
			//If there is any values in inclusion list, only include those in the list
			return includedOrgunitUuids.contains(orgunitUuid);
		}
		if (excludedOrgunitUuids != null && !excludedOrgunitUuids.isEmpty()) {
			//If there is any values in exclusion list, do not include those, but include all others
			return !excludedOrgunitUuids.contains(orgunitUuid);
		}
		return true;
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
		Attestation attestation = findSystemUsersAttestationFor(assignment, when).orElse(null);
		// It the user has been deleted it will be null, and we cannot do anything sensible
		final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
		if (historyUser == null) {
			return;
		}
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				attestation = createItSystemUsersAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		} else {
			// If we have another open attestation for this it-system the responsible manager might have changed, in that
			// case we need to delete the old attestation (unless its already been verified).
			if (!attestation.getResponsibleUserUuid().equals(assignment.getResponsibleUserUuid())) {
				log.info("It-system responsible changed updating attestation");
				attestation.setResponsibleUserUuid(assignment.getResponsibleUserUuid());
				attestation.setResponsibleUserId(historyUser.getUserUserId());
			}
		}
		addUser(attestation, assignment);
		if (cntUA++ % 100 == 0) {
			log.info("Attestation system user " + attestation.getUuid() + " found, progress count=" + cntUA);
		}
	}

	private void ensureWeHaveSystemUsersAttestationForOu(final AttestationRun run,
														 final AttestationOuRoleAssignment assignment,
														 final LocalDate when) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		Attestation attestation = findSystemUsersAttestationFor(assignment, when).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				attestation = createItSystemUsersAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		}
		if (cntUA++ % 100 == 0) {
			log.info("Attestation system user " + attestation.getUuid() + " found, progress count=" + cntUA);
		}
	}

	static int cntOu = 0;

	private void ensureWeHaveOrganisationAttestationForOu(final AttestationRun run,
														  final AttestationOuRoleAssignment assignment,
														  final LocalDate when) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		Attestation attestation = findOrganisationAttestationFor(assignment, when).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				attestation = createOrganisationUsersAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		}
		if (cntOu++ % 100 == 0) {
			log.info("Attestation for organisation role " + attestation.getUuid() + " found, progress count=" + cntOu);
		}
	}

	static int cnt = 0;

	private void ensureWeHaveOrganisationAttestationFor(final AttestationRun run,
														final AttestationUserRoleAssignment assignment,
														final LocalDate when) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		Attestation attestation = findOrganisationAttestationFor(assignment, when).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				attestation = createOrganisationUsersAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		}
		addUser(attestation, assignment);
		if (cnt++ % 100 == 0) {
			log.info("Attestation for organisation role " + attestation.getUuid() + " found, progress count=" + cnt);
		}
	}

	static int cnt_md_ou = 0;
	private void ensureManagerDelegateAttestation(final AttestationRun run,
												  final AttestationOuRoleAssignment assignment,
												  final LocalDate when
	) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		Attestation attestation = findManagerDelegateAttestationFor(assignment.getResponsibleOuUuid(), when).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				attestation = createManagerDelegateAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		}
		if (cnt_md_ou++ % 100 == 0) {
			log.info("Attestation for manager delegate ou role " + attestation.getUuid() + " found, progress count=" + cnt_md_ou);
		}
	}



	static int cnt_md_u = 0;
	private void ensureManagerDelegateAttestation(final AttestationRun run,
												  final AttestationUserRoleAssignment assignment,
												  final LocalDate when
	) {
		if (shouldDisregardAssignment(run, assignment.isSensitiveRole(), assignment.isExtraSensitiveRole())) {
			return;
		}
		Attestation attestation = findManagerDelegateAttestationFor(assignment.getResponsibleOuUuid(), when).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				attestation = createManagerDelegateAttestationFor(run, assignment, run.isSensitive(), when, run.getDeadline());
			}
		}
		addUser(attestation, assignment);
		if (cnt_md_u++ % 100 == 0) {
			log.info("Attestation for manager delegate user role " + attestation.getUuid() + " found, progress count=" + cnt_md_u);
		}
	}

	static int ouAdCnt = 0;

	private void ensureWeHaveOrganisationAttestationFor(final AttestationRun run,
														final String responsibleOuUuid,
														final LocalDate when) {
		if (run.isSensitive() || run.isExtraSensitive()) {
			// This method creates a OU attestations even though there is no role assignment, this is
			// used when AD attestation is enabled an all OUs should be attestated.
			// So we NEVER wants this to happen for sensitive runs.
			return;
		}
		Attestation attestation = findOrganisationAttestationFor(responsibleOuUuid, when).orElse(null);
		if (attestation == null) {
			if (run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
				// Deadline is in the future do not create an attestation entity yet
				return;
			} else {
				// Deadline is soon we need to create a new attestation
				final Optional<OrgUnit> optOu = orgUnitDao.findById(responsibleOuUuid);
				if (optOu.isPresent()) {
					attestation = createOrganisationUsersAttestationFor(run, responsibleOuUuid, optOu.get().getName(), false, when, run.getDeadline());
				}
			}
		}
		if (ouAdCnt++ % 100 == 0) {
			log.info("Attestation for organisation " + attestation.getUuid() + " found, progress count=" + ouAdCnt);
		}
	}

	private Optional<Attestation> findSystemUsersAttestationFor(final AttestationUserRoleAssignment assignment, final LocalDate when) {
		return attestationDao.findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(
				Attestation.AttestationType.IT_SYSTEM_ATTESTATION, assignment.getItSystemId(), when);
	}

	private Optional<Attestation> findSystemUsersAttestationFor(final AttestationOuRoleAssignment assignment, final LocalDate when) {
		return attestationDao.findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(
				Attestation.AttestationType.IT_SYSTEM_ATTESTATION, assignment.getItSystemId(), when);
	}

	private Optional<Attestation> findOrganisationAttestationFor(final AttestationUserRoleAssignment assignment, final LocalDate when) {
		return attestationDao.findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(
				Attestation.AttestationType.ORGANISATION_ATTESTATION, assignment.getResponsibleOuUuid(), when);
	}

	private Optional<Attestation> findOrganisationAttestationFor(final AttestationOuRoleAssignment assignment, final LocalDate when) {
		return attestationDao.findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(
				Attestation.AttestationType.ORGANISATION_ATTESTATION, assignment.getResponsibleOuUuid(), when);
	}


	private Optional<Attestation> findOrganisationAttestationFor(final String ouUuid, final LocalDate when) {
		return attestationDao.findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(
				Attestation.AttestationType.ORGANISATION_ATTESTATION, ouUuid, when);
	}

	private Optional<Attestation> findManagerDelegateAttestationFor(final String responsibleOuUuid, final LocalDate when) {
		return attestationDao.findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(
				Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, responsibleOuUuid, when);
	}

	private Attestation createOrganisationUsersAttestationFor(final AttestationRun run,
															  final AttestationUserRoleAssignment assignment, boolean sensitive,
															  final LocalDate when, final LocalDate deadline) {
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION)
				.sensitive(sensitive)
				.responsibleOuUuid(assignment.getResponsibleOuUuid())
				.responsibleOuName(assignment.getResponsibleOuName())
						.responsibleUserUuid(assignment.getResponsibleUserUuid())
						.responsibleUserId(assignment.getUserId())

				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);
	}

	private Attestation createOrganisationUsersAttestationFor(final AttestationRun run,
															  final AttestationOuRoleAssignment assignment, boolean sensitive,
															  final LocalDate when, final LocalDate deadline) {
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION)
				.sensitive(sensitive)
				.responsibleOuUuid(assignment.getResponsibleOuUuid())
				.responsibleOuName(assignment.getResponsibleOuName())
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);
	}

	private Attestation createOrganisationUsersAttestationFor(final AttestationRun run,
															  final String responsibleOuUuid,
															  final String responsibleOuName,
															  boolean sensitive, final LocalDate when,
															  final LocalDate deadline) {
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION)
				.sensitive(sensitive)
				.responsibleOuUuid(responsibleOuUuid)
				.responsibleOuName(responsibleOuName)
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);
	}

	private Attestation createManagerDelegateAttestationFor(final AttestationRun run,
															AttestationOuRoleAssignment assignment,
															boolean sensitive,
															final LocalDate when, final LocalDate deadline) {
		var attestation = attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION)
				.sensitive(sensitive)
				.responsibleOuUuid(assignment.getResponsibleOuUuid())
				.responsibleOuName(assignment.getResponsibleOuName())
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);

		return attestation;
	}

	private Attestation createManagerDelegateAttestationFor(final AttestationRun run,
															AttestationUserRoleAssignment assignment,
															boolean sensitive,
															final LocalDate when, final LocalDate deadline) {
		var attestation = attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION)
				.sensitive(sensitive)
				.responsibleOuUuid(assignment.getResponsibleOuUuid())
				.responsibleOuName(assignment.getResponsibleOuName())
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);

		return attestation;
	}

	private Attestation createItSystemUsersAttestationFor(final AttestationRun run,
														  final AttestationUserRoleAssignment assignment, boolean sensitive,
														  final LocalDate when, final LocalDate deadline) {
		final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
				.sensitive(sensitive)
				.itSystemId(assignment.getItSystemId())
				.itSystemName(assignment.getItSystemName())
				.responsibleUserId(historyUser.getUserUserId())
				.responsibleUserUuid(assignment.getResponsibleUserUuid())
				.deadline(deadline)
				.createdAt(when)
				.uuid(UUID.randomUUID().toString())
				.build()
		);
	}


	private Attestation createItSystemUsersAttestationFor(final AttestationRun run,
														  final AttestationOuRoleAssignment assignment, boolean sensitive,
														  final LocalDate when, final LocalDate deadline) {
		final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
		return attestationDao.save(Attestation.builder()
				.attestationRun(run)
				.attestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
				.sensitive(sensitive)
				.itSystemId(assignment.getItSystemId())
				.itSystemName(assignment.getItSystemName())
				.responsibleUserId(historyUser.getUserUserId())
				.responsibleUserUuid(assignment.getResponsibleUserUuid())
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
