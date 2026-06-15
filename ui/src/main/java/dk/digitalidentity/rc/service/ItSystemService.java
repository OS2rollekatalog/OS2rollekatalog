package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.ItSystemDao;
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
	private RoleGroupService roleGroupService;

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
	private HistoricItSystemAssignmentService historicItSystemAssignmentService;

	@Autowired
	private HistoryUserDao historyUserDao;

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
		ItSystem itSystem = itSystemDao.findById(id).orElseThrow();
		List<String> newUuids = users.stream().map(User::getUuid).toList();

		itSystem.getAttestationResponsibles().clear();
		entityManager.flush();
		users.forEach(itSystem::addAttestationResponsible);

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
		ItSystem itSystem = itSystemDao.findById(id).orElseThrow();
		itSystem.getSystemOwners().clear();
		entityManager.flush();
		users.forEach(itSystem::addSystemOwner);
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

		List<User> allUsers = userService.getAll();
		for (ItSystem system : systems) {
			handleSingleITSystemKitosOwnerAndResponsible(system, allUsers);
		}
	}

	public void handleSingleITSystemKitosOwnerAndResponsible(ItSystem system, List<User> allUsers) {
		system.getAttestationResponsibles().clear();
		system.getSystemOwners().clear();

		if (system.getKitosITSystem().getKitosUsers() != null) {
			User owner = findUserForKitosRole(KitosRole.SYSTEM_OWNER, system, allUsers);
			User responsible = findUserForKitosRole(KitosRole.SYSTEM_RESPONSIBLE, system, allUsers);

			if (owner != null) {
				system.addSystemOwner(owner);
			}
			if (responsible != null) {
				system.addAttestationResponsible(responsible);
			}
		}
		itSystemDao.save(system);
		List<User> newResponsibles = getAttestationResponsibles(system);
		updateAttestationResponsibles(system.getId(), newResponsibles);
	}

	private User findUserForKitosRole(KitosRole kitosRole, ItSystem system, List<User> allUsers) {
		List<KitosITSystemUser> kitosUsers = system.getKitosITSystem().getKitosUsers().stream()
				.filter(u -> u.getRole().equals(kitosRole))
				.toList();

		for (KitosITSystemUser kitosUser : kitosUsers) {
            // Match by email, before we match by name to avoid same name troubles
			return allUsers.stream()
					.filter(u ->  kitosUser.getEmail().equalsIgnoreCase(u.getEmail()))
					.findFirst()
                    .orElseGet(() ->
                            allUsers.stream()
                                    .filter(u -> kitosUser.getName().equalsIgnoreCase(u.getName()))
                                    .findFirst().orElse(null));
		}

		return null;
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
