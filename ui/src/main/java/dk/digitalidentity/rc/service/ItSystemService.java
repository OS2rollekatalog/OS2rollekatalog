package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemAttestationResponsible;
import dk.digitalidentity.rc.dao.model.ItSystemSystemOwner;
import dk.digitalidentity.rc.dao.model.KitosITSystemUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KitosRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricAssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ItSystemService {

	@Autowired
	private ItSystemDao itSystemDao;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private AttestationDao attestationDao;

	@Autowired
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Autowired
	private HistoricAssignmentDao historicAssignmentDao;

	@Autowired
	private HistoricOuAssignmentDao historicOuAssignmentDao;

	@Autowired
	private HistoricAssignmentService historicAssignmentService;

	@Autowired
	private HistoricItSystemAssignmentService historicItSystemAssignmentService;

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private UserRoleCleanupService userRoleCleanupService;

	@Transactional(readOnly = true)
	public List<ItSystem> getAllByIdInAndDeletedFalse(Collection<Long> ids) {
		return itSystemDao.findByIdInAndDeletedFalse(ids);
	}

	public Set<ItSystem> findAllByIdIn(Collection<Long> ids) {
		return itSystemDao.findAllByDeletedFalseAndIdIn(ids);
	}

	@Transactional(readOnly = true)
	public List<ItSystem> getAllByDeletedFalse() {
		return itSystemDao.findAllByDeletedFalse();
	}

	public List<ItSystem> getAll() {
		List<ItSystem> result = itSystemDao.findAll();
		result = filterDeleted(result);

		return result.stream().sorted((o1, o2) -> o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase())).collect(Collectors.toList());
	}

	public ItSystem getFirstByIdentifier(String identifier) {
		List<ItSystem> result = itSystemDao.findByIdentifier(identifier);
		result = filterDeleted(result);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	public ItSystem getFirstByName(String name) {
		List<ItSystem> result = itSystemDao.findByName(name);
		result = filterDeleted(result);
		if (result != null && result.size() > 0) {
			return result.get(0);
		}

		return null;
	}

	@AuditLogIntercepted
	public ItSystem save(ItSystem itSystem) {
		return itSystemDao.save(itSystem);
	}

	/**
	 * Replaces the attestation responsibles on the IT system. Open (unverified) attestations belonging
	 * to a removed responsible are reassigned to one of the remaining responsibles (lexicographically
	 * smallest UUID, for stable ordering). If no responsibles remain, the open attestations are deleted.
	 * Verified attestations are never touched.
	 */
	@Transactional
	public void updateAttestationResponsibles(long id, List<User> users) {
		// Hent under en pessimistisk skrivelås (SELECT … FOR UPDATE) så samtidige ændringer af
		// responsible-collectionen serialiseres — ellers kunne to transaktioner begge planlægge
		// orphanRemoval-DELETE af de samme rækker (StaleStateException: expected 1 but was 0).
		// På REST-stien er det en frisk load i en ny persistence context. Kitos-syncen, der kalder
		// internt fra en langlivet transaktion, har allerede refreshet+låst systemet (se
		// handleSingleITSystemKitosOwnerAndResponsible), så vi får den friske, låste instans her.
		ItSystem itSystem = itSystemDao.findByIdForUpdate(id).orElseThrow();
		List<String> newUuids = users.stream().map(User::getUuid).toList();

		// Set-diff frem for clear()+reinsert: rør kun de responsibles der faktisk ændrer sig, så
		// uændrede it_system_attestation_responsible-rækker hverken slettes (racet) eller giver
		// duplicate-key på uk_it_system_att_resp ved delete+reinsert af samme (itsystem, user).
		Set<String> desiredUuids = new HashSet<>(newUuids);
		Set<String> currentUuids = itSystem.getAttestationResponsibles().stream()
			.map(r -> r.getUser().getUuid()).collect(Collectors.toSet());
		if (!currentUuids.equals(desiredUuids)) {
			itSystem.getAttestationResponsibles().removeIf(r -> !desiredUuids.contains(r.getUser().getUuid()));
			users.stream()
				.filter(u -> !currentUuids.contains(u.getUuid()))
				.forEach(itSystem::addAttestationResponsible);
		}

		// Update the AttestationResponsibleCollection for this IT system to match the new responsible list.
		// Open (unverified) attestations pointing to this collection keep their reference — the collection
		// now reflects the current set of responsibles, so reassignment happens implicitly.
		// If no responsibles remain, delete open attestations since there is nobody to attest them.
		AttestationResponsibleCollection collection = attestationResponsibleCollectionDao.findFirstByItSystemId(id)
			.orElseGet(() -> attestationResponsibleCollectionDao.save(
				AttestationResponsibleCollection.builder().itSystemId(id).build()));
		collection.getUsersUuid().clear();
		if (newUuids.isEmpty()) {
			List<Attestation> open = attestationDao.findByItSystemIdAndResponsibleCollectionIdAndVerifiedAtIsNull(id, collection.getId());
			for (Attestation a : open) {
				a.getOrganisationUserAttestationEntries().clear();
				a.getItSystemUserAttestationEntries().clear();
				a.getItSystemUserRoleAttestationEntries().clear();
				a.getItSystemOrganisationAttestationEntries().clear();
				a.getUsersForAttestation().clear();
				a.getMails().clear();
			}
			attestationDao.deleteAll(open);
			log.info("Deleted {} open attestations (no remaining responsibles) on IT system {}", open.size(), id);
		} else {
			collection.getUsersUuid().addAll(newUuids);
			attestationResponsibleCollectionDao.save(collection);
			// Backfill any historic_assignment rows for this IT system that were written before
			// the collection existed (responsible_collection_id was null at write time).
			historicAssignmentDao.backfillResponsibleCollectionId(id, collection.getId());
			historicOuAssignmentDao.backfillResponsibleCollectionId(id, collection.getId());
			// historic_it_system_assignment har collection-id i sin recordHash, så backfill skal ske
			// i Java hvor hashen kan genberegnes — en ren SQL-backfill efterlader stale hashes som
			// fjern-events ikke kan lukke på.
			historicItSystemAssignmentService.repairResponsibleCollectionForItSystem(id);
			log.info("Updated responsible collection for IT system {} to {} responsibles", id, newUuids.size());
		}
	}

	/**
	 * Brings an IT system's {@link AttestationResponsibleCollection} back in sync with the live
	 * responsible list.
	 * <p>
	 * V1_351 seeded the collections from the live responsible list, but V1_352 then layered the
	 * historic attestation rows on top - the union of everyone ever responsible for the system.
	 * Recipients resolve from the collection
	 * ({@code AttestationEmailNotificationService.findAttesters}) while page access is gated on the
	 * live list, so a former responsible keeps receiving attestation mails they cannot act on, and a
	 * system with nobody left responsible still shows in the admin overview.
	 * <p>
	 * Only the membership drifted, never the collection id, so the two paths differ in cost: with
	 * nobody left responsible we go through {@link #updateAttestationResponsibles}, which also deletes
	 * the open attestations along with the child rows that have no ON DELETE CASCADE. Otherwise we
	 * rewrite the membership here instead, because that method would additionally run three bulk
	 * UPDATEs over the (large, and on it_system_id unindexed) historic tables per system, and clear
	 * responsible_ou_uuid on every open row it touches - backfills that repair a collection id which
	 * has not changed.
	 *
	 * @return true if the collection diverged and was rewritten
	 */
	@Transactional
	public boolean reconcileAttestationResponsibleCollection(long itSystemId) {
		AttestationResponsibleCollection collection = attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId).orElse(null);
		if (collection == null) {
			return false;
		}
		ItSystem itSystem = itSystemDao.findById(itSystemId).orElse(null);
		if (itSystem == null) {
			// The system is gone - the FK cascade (ON DELETE CASCADE) already removed the collection.
			return false;
		}
		List<User> liveResponsibles = getAttestationResponsibles(itSystem);
		Set<String> liveUuids = liveResponsibles.stream().map(User::getUuid).collect(Collectors.toSet());
		Set<String> snapshotUuids = new HashSet<>(collection.getUsersUuid());
		if (liveUuids.equals(snapshotUuids)) {
			return false;
		}
		log.info("Reconciling responsible collection for IT system {}: snapshot {} -> live {}", itSystemId, snapshotUuids, liveUuids);
		if (liveUuids.isEmpty()) {
			updateAttestationResponsibles(itSystemId, liveResponsibles);
		} else {
			collection.getUsersUuid().clear();
			collection.getUsersUuid().addAll(liveUuids);
			attestationResponsibleCollectionDao.save(collection);
		}
		return true;
	}

	@Transactional
	public void backfillHistoricResponsibleCollection(long itSystemId) {
		attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId).ifPresent(collection -> {
			historicAssignmentDao.backfillResponsibleCollectionId(itSystemId, collection.getId());
			historicOuAssignmentDao.backfillResponsibleCollectionId(itSystemId, collection.getId());
			historicItSystemAssignmentService.repairResponsibleCollectionForItSystem(itSystemId);
		});
	}

	@Transactional
	public void updateSystemOwners(long id, List<User> users) {
		// Hent under pessimistisk skrivelås — se updateAttestationResponsibles for samspillet med syncen.
		ItSystem itSystem = itSystemDao.findByIdForUpdate(id).orElseThrow();

		// Set-diff frem for clear()+reinsert: rør kun de ejere der faktisk ændrer sig. Undgår
		// både unødig delete+reinsert af uændrede rækker (og dermed orphanRemoval-racet på dem)
		// og duplicate-key på uk_it_system_owner ved delete+reinsert af samme (itsystem, user).
		Set<String> desiredUuids = users.stream().map(User::getUuid).collect(Collectors.toSet());
		Set<String> currentUuids = itSystem.getSystemOwners().stream()
			.map(o -> o.getUser().getUuid()).collect(Collectors.toSet());
		if (currentUuids.equals(desiredUuids)) {
			return;
		}
		itSystem.getSystemOwners().removeIf(o -> !desiredUuids.contains(o.getUser().getUuid()));
		users.stream()
			.filter(u -> !currentUuids.contains(u.getUuid()))
			.forEach(itSystem::addSystemOwner);
	}

	@AuditLogIntercepted
	public ItSystem softDelete(ItSystem itSystem) {
		itSystem.setDeleted(true);
		itSystem.setDeletedTimestamp(new Date());
		return itSystemDao.save(itSystem);
	}

	public ItSystem getById(long id) {

		return filterDeleted(itSystemDao.findById(id).orElse(null));
	}

	public Optional<ItSystem> getOptionalById(long id) {
		return itSystemDao.findById(id);
	}

	public ItSystem getByUuid(String uuid) {
		return filterDeleted(itSystemDao.findByUuid(uuid));
	}

	public ItSystem getByUuidIncludingDeleted(String uuid) {
		return itSystemDao.findByUuid(uuid);
	}

	public List<ItSystem> getBySystemType(ItSystemType systemType) {
		return filterDeleted(itSystemDao.findBySystemType(systemType));
	}

	@Transactional
	public List<ItSystem> getBySystemType(ItSystemType systemType, Consumer<ItSystem> consumer) {
		List<ItSystem> result = itSystemDao.findBySystemType(systemType);

		if (consumer != null) {
			result.forEach(consumer);
		}

		return result;
	}

	public List<ItSystem> getBySystemTypeIn(List<ItSystemType> systemTypes) {
		return filterDeleted(itSystemDao.findBySystemTypeIn(systemTypes));
	}

	public List<ItSystem> getBySystemTypeIncludingDeleted(ItSystemType systemType) {
		return itSystemDao.findBySystemType(systemType);
	}

	public List<ItSystem> getBySubscribedToNotNull() {
		return filterDeleted(itSystemDao.findBySubscribedToNotNull());
	}

	public long count() {
		return itSystemDao.countByDeletedFalseAndHiddenFalse();
	}

	public List<ItSystem> findByIdentifier(String identifier) {
		return filterDeleted(itSystemDao.findByIdentifier(identifier));
	}

	public List<ItSystem> findBySystemOwner(User user) {
		return filterDeleted(itSystemDao.findBySystemOwners_User(user));
	}

	public List<ItSystem> findAllForAttestation() {
		return itSystemDao.findByDeletedFalseAndAttestationExemptFalse();
	}

	/**
	 * This method will return a list of it-systems looked up by identifier, if no identifier is supplier all it-systems are returned.
	 * If an identifier is set, we will try using the identifier as identifier, uuid or id and return the found it-system.
	 */
	public List<ItSystem> findByAnyIdentifier(final String itSystemIdentifier) {
		if (itSystemIdentifier == null || itSystemIdentifier.isEmpty()) {
			return getAll();
		}
		final List<ItSystem> itSystems = findByIdentifier(itSystemIdentifier);
		if (itSystems != null && !itSystems.isEmpty()) {
			return itSystems;
		}
		final ItSystem itSystem = getByUuid(itSystemIdentifier);
		if (itSystem != null) {
			return Collections.singletonList(itSystem);
		}
		try {
			final ItSystem itSystemById = getById(Long.parseLong(itSystemIdentifier));
			if (itSystemById != null) {
				return Collections.singletonList(itSystemById);
			}
		} catch (Exception ex) {
			 // ignore
		}
		return Collections.emptyList();
	}

	public List<ItSystem> findByAttestationResponsible(User user) {
		return itSystemDao.findByAttestationResponsibles_User(user);
	}

	public List<ItSystem> findByAttestationResponsibleOrSystemOwner(User user) {
		return itSystemDao.findByAttestationResponsibles_UserOrSystemOwners_User(user, user);
	}

	// TODO: use count
	public int getUnusedUserRolesCount(ItSystem itSystem) {
		int sum = 0;

		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			if (assignmentService.countAllUsersWithDirectUserRole(userRole) > 0) {
				continue;
			}

			if (orgUnitService.countAllWithRole(userRole) > 0) {
				continue;
			}

			sum++;
		}

		return sum;
	}

	public List<ItSystem> getVisible() {
		List<ItSystem> result = itSystemDao.findByHiddenFalse();
		result = filterDeleted(result);

		return result.stream().sorted((o1, o2) -> o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase())).collect(Collectors.toList());
	}

	public List<String> getOUFilterUuidsWithChildren(ItSystem itSystem) {
		Set<String> selectedOUs = new HashSet<>();
		for (OrgUnit ou : itSystem.getOrgUnitFilterOrgUnits()) {
			if (!selectedOUs.contains(ou.getUuid())) {
				addChildrenRecursive(ou, selectedOUs);
			}
		}

		return new ArrayList<>(selectedOUs);
	}

	private void addChildrenRecursive(OrgUnit ou, Set<String> selectedOUs) {
		selectedOUs.add(ou.getUuid());

		if (ou.getChildren() == null || ou.getChildren().isEmpty()) {
			return;
		}

		for (OrgUnit child : ou.getChildren()) {
			addChildrenRecursive(child, selectedOUs);
		}
	}

	// TODO - refactoring target
	@Transactional(rollbackFor = Exception.class)
	public void permanentlyDelete() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());
		cal.add(Calendar.DAY_OF_MONTH, -3);
		Date sixMonthsAgo = cal.getTime();

		List<ItSystem> deleted = itSystemDao.findByDeletedTrue();
		for (ItSystem itSystem : deleted) {
			if (itSystem.getDeletedTimestamp().before(sixMonthsAgo)) {
				log.info("Attempting to delete it-system " + itSystem.getName() + " with id = " + itSystem.getId());

				// these are build-in, cannot delete
				if (itSystem.getSystemType().equals(ItSystemType.KSPCICS) || itSystem.getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)) {
					continue;
				}

				// delete affected user roles — cleanup-service fjerner rollebuket-,
				// direkte bruger- og OU-tildelinger før sletningen. RoleChangeInterceptor
				// queuer berørte brugere til genberegning, så orphan CAs efterladt af
				// ON DELETE SET NULL ryddes op af recalculation-jobbet.
				List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
				for (UserRole userRole : userRoles) {
					userRoleCleanupService.deleteWithCleanup(userRole);
				}

				// delete affected system roles
				List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
				for (SystemRole systemRole : systemRoles) {
					systemRoleService.delete(systemRole);
				}

				// delete itsystem
				itSystemDao.delete(itSystem);
			}
		}
	}

	@Transactional
	public void syncKitosOwnersAndResponsibles() {
		List<ItSystem> systems = itSystemDao.findByKitosITSystemNotNull();
		if (systems.isEmpty()) {
			return;
		}

		// Ét opslag for hele kørslen frem for en fuld scanning pr. Kitos-bruger pr. system.
		KitosUserLookup lookup = KitosUserLookup.of(userService.getAll());
		for (ItSystem system : systems) {
			handleSingleITSystemKitosOwnerAndResponsible(system, lookup);
		}
	}

	// @Transactional sikrer en transaktion når metoden kaldes direkte fra REST-stien
	// (ItSystemRestController). Kaldt fra syncKitosOwnersAndResponsibles er det et internt
	// kald, der bypasser proxien, så den deltager blot i den allerede åbne transaktion.
	@Transactional
	public void handleSingleITSystemKitosOwnerAndResponsible(ItSystem system, List<User> allUsers) {
		handleSingleITSystemKitosOwnerAndResponsible(system, KitosUserLookup.of(allUsers));
	}

	private void handleSingleITSystemKitosOwnerAndResponsible(ItSystem system, KitosUserLookup lookup) {
		List<User> desiredOwners = findUsersForKitosRole(KitosRole.SYSTEM_OWNER, system, lookup);
		List<User> desiredResponsibles = findUsersForKitosRole(KitosRole.SYSTEM_RESPONSIBLE, system, lookup);

		// Kun rør collections når Kitos faktisk afviger fra den nuværende tilstand. I det normale
		// tilfælde (stabilt ejerskab) er sættene uændrede, så vi undgår både delete+reinsert af de
		// samme rækker (som ellers gav duplicate key på uk_it_system_att_resp/uk_it_system_owner) og
		// den dyre attestation-/historik-oprydning i updateAttestationResponsibles.
		// sameUsers-tjekkene er en billig pre-filtrering på den (muligvis stale) in-memory tilstand fra
		// sync'ens langlivede transaktion. Afviger Kitos, refresher vi systemet ÉN gang under en
		// pessimistisk lås: refresh(PESSIMISTIC_WRITE) udsteder SELECT … FOR UPDATE (et current read forbi
		// tx-snapshot'et) og overskriver de stale collections med committet tilstand. Det er afgørende —
		// ikke blot en lock() — for ellers kunne de efterfølgende set-diff-mutationer planlægge en
		// orphanRemoval-DELETE af en række en samtidig transaktion allerede har slettet. Kun ÉN refresh
		// (ikke én pr. mutator): en anden refresh på samme entitet ville kassere den førstes pending ændringer.
		// Bagefter kører mutatorerne på den friske, låste instans (deres findByIdForUpdate returnerer den).
		boolean ownersDiffer = !sameUsers(system.getSystemOwners().stream().map(ItSystemSystemOwner::getUser).toList(), desiredOwners);
		boolean responsiblesDiffer = !sameUsers(getAttestationResponsibles(system), desiredResponsibles);
		if (!ownersDiffer && !responsiblesDiffer) {
			return;
		}

		entityManager.refresh(system, LockModeType.PESSIMISTIC_WRITE);
		if (ownersDiffer) {
			updateSystemOwners(system.getId(), desiredOwners);
		}
		if (responsiblesDiffer) {
			updateAttestationResponsibles(system.getId(), desiredResponsibles);
		}
	}

	private boolean sameUsers(List<User> current, List<User> desired) {
		Set<String> currentUuids = current.stream().map(User::getUuid).collect(Collectors.toSet());
		Set<String> desiredUuids = desired.stream().map(User::getUuid).collect(Collectors.toSet());
		return currentUuids.equals(desiredUuids);
	}

	private List<User> findUsersForKitosRole(KitosRole kitosRole, ItSystem system, KitosUserLookup lookup) {
		Map<String, User> matched = new LinkedHashMap<>();
		List<UUID> unmatched = new ArrayList<>();
		for (KitosITSystemUser kitosUser : system.getKitosITSystem().getKitosUsers()) {
			if (!kitosUser.getRole().equals(kitosRole)) {
				continue;
			}
			// Slås op uafhængigt og dedupliceres på uuid: gjorde vi matchet afhængigt af hvem de øvrige
			// tog, kunne en Kitos-bruger med allerede-fundet bruger lande på en navnefælle i stedet.
			lookup.uniqueByEmail(kitosUser.getEmail())
					.or(() -> lookup.uniqueByName(kitosUser.getName()))
					.ifPresentOrElse(u -> matched.put(u.getUuid(), u), () -> unmatched.add(kitosUser.getKitosUuid()));
		}

		if (!unmatched.isEmpty()) {
			log.warn("{} Kitos users with role {} on IT system {} matched no unique role catalogue user, skipped: {}",
					unmatched.size(), kitosRole, system.getId(), unmatched);
		}

		return List.copyOf(matched.values());
	}

	// Kræver ét entydigt match: to navnefæller må ikke gøre valget til et lotteri.
	private record KitosUserLookup(Map<String, List<User>> byEmail, Map<String, List<User>> byName) {

		private static KitosUserLookup of(List<User> allUsers) {
			return new KitosUserLookup(index(allUsers, User::getEmail), index(allUsers, User::getName));
		}

		private static Map<String, List<User>> index(List<User> allUsers, Function<User, String> key) {
			return allUsers.stream()
					.filter(u -> hasValue(key.apply(u)))
					.collect(Collectors.groupingBy(u -> key.apply(u).trim().toLowerCase()));
		}

		private Optional<User> uniqueByEmail(String email) {
			return unique(byEmail, email);
		}

		private Optional<User> uniqueByName(String name) {
			return unique(byName, name);
		}

		private static Optional<User> unique(Map<String, List<User>> index, String kitosValue) {
			if (!hasValue(kitosValue)) {
				return Optional.empty();
			}
			List<User> candidates = index.get(kitosValue.trim().toLowerCase());
			return candidates != null && candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
		}

		private static boolean hasValue(String value) {
			return value != null && !value.isBlank();
		}
	}

	public long systemResponsibleCount(User user){
		return itSystemDao.countByAttestationResponsibles_User(user);
	}

	//UTILITY METHODS

	private List<ItSystem> filterDeleted(List<ItSystem> itSystems) {
		return itSystems == null ? null
				: itSystems.stream().filter(its -> !its.isDeleted()).toList();
	}

	private ItSystem filterDeleted(ItSystem itSystem) {
		if (itSystem == null) {
			return null;
		}
		return itSystem.isDeleted() ? null : itSystem;
	}

	@Transactional
	public void saveAll(List<ItSystem> itSystems) {
		itSystemDao.saveAll(itSystems);
	}

	@Transactional
	public void deleteAll(List<ItSystem> itSystems) {
		historicAssignmentService.closeOpenForDeletedItSystems(itSystems);
		itSystemDao.deleteAll(itSystems);
	}

	@Transactional(readOnly = true)
	public List<User> getAttestationResponsibles(ItSystem itSystem) {
		return itSystem.getAttestationResponsibles().stream()
			.map(ItSystemAttestationResponsible::getUser)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<String> getAttestationResponsibleUserIds(ItSystem itSystem) {
		return itSystem.getAttestationResponsibles().stream()
			.map(r -> r.getUser().getUserId())
			.toList();
	}

	@Transactional(readOnly = true)
	public List<String> getAttestationResponsibleUuids(ItSystem itSystem) {
		return itSystem.getAttestationResponsibles().stream()
			.map(r -> r.getUser().getUuid())
			.toList();
	}

	@Transactional(readOnly = true)
	public String getAttestationResponsibleNames(ItSystem itSystem) {
		return itSystem.getAttestationResponsibles().stream()
			.map(r -> r.getUser().getName())
			.collect(Collectors.joining(","));
	}

	@Transactional(readOnly = true)
	public String getAttestationResponsibleUserIdsJoined(ItSystem itSystem) {
		return itSystem.getAttestationResponsibles().stream()
			.map(r -> r.getUser().getUserId())
			.collect(Collectors.joining(","));
	}

	@Transactional(readOnly = true)
	public List<User> getSystemOwners(ItSystem itSystem) {
		return itSystem.getSystemOwners().stream()
			.map(ItSystemSystemOwner::getUser)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<String> getSystemOwnerUuids(ItSystem itSystem) {
		return itSystem.getSystemOwners().stream()
			.map(o -> o.getUser().getUuid())
			.toList();
	}

	@Transactional(readOnly = true)
	public String getSystemOwnerNames(ItSystem itSystem) {
		return itSystem.getSystemOwners().stream()
			.map(o -> o.getUser().getName())
			.collect(Collectors.joining(","));
	}

	@Transactional(readOnly = true)
	public String getSystemOwnerUserIdsJoined(ItSystem itSystem) {
		return itSystem.getSystemOwners().stream()
			.map(o -> o.getUser().getUserId())
			.collect(Collectors.joining(","));
	}
}
