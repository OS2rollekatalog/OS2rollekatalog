package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class SystemRoleService {
	private final SystemRoleDao systemRoleDao;
	private final UserRoleService userRoleService;
	private final AssignmentService assignmentService;
	private final EmailTemplateService emailTemplateService;
	private final EmailQueueService emailQueueService;

	// @Lazy: PendingADUpdateService field-injects SystemRoleService, so injecting it via this bean's
	// @RequiredArgsConstructor would create a constructor-level dependency cycle. @Lazy breaks it cleanly
	// without relying on the global spring.main.allow-circular-references flag.
	@Lazy
	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	public SystemRole getById(long id) {
		return systemRoleDao.findById(id);
	}

	public Optional<SystemRole> getOptionalById(long id) {
		return Optional.ofNullable(systemRoleDao.findById(id));
	}

	public Map<Long, SystemRole> getByIdsAsMap(Collection<Long> ids) {
		return systemRoleDao.findByIdIn(ids).stream()
			.collect(Collectors.toMap(SystemRole::getId, Function.identity()));
	}

	public List<SystemRole> getByItSystem(ItSystem itSystem) {
		return systemRoleDao.findByItSystem(itSystem);
	}

	public List<SystemRole> getByUserUuid(String userUuid) {
		return systemRoleDao.findDistinctByUserUuid(userUuid);
	}

	public List<SystemRole> getByUserUuidAndItSystemIds(String userUuid, Collection<Long> itSystemIds) {
		return systemRoleDao.findDistinctByUserUuidAndItSystemIdIn(userUuid, itSystemIds);
	}
	
	public List<SystemRole> getByItSystemIds(Collection<Long> itSystemIds) {
		return systemRoleDao.findByItSystemIdIn(itSystemIds);
	}

	@Transactional
	public List<SystemRole> getByItSystem(ItSystem itSystem, Consumer<SystemRole> consumer) {
		List<SystemRole> systemRoles = systemRoleDao.findByItSystem(itSystem);

		if (consumer != null) {
			systemRoles.forEach(consumer);
		}

		return systemRoles;
	}

	public SystemRole getByUuid(String uuid) {
		return systemRoleDao.findByUuid(uuid);
	}

	public List<SystemRole> findByItSystemAndUuidNotNull(ItSystem itSystem) {
		return systemRoleDao.findByItSystemAndUuidNotNull(itSystem);
	}

	public SystemRole getFirstByIdentifierAndItSystemId(String identifier, long itSystemId) {
		List<SystemRole> result = systemRoleDao.findByIdentifierAndItSystemId(identifier, itSystemId);
		if (result != null && !result.isEmpty()) {
			return result.getFirst();
		}

		return null;
	}

	@Transactional
	public SystemRole save(SystemRole systemRole) {
		return systemRoleDao.save(systemRole);
	}

	/**
	 * Ændrer vægtningen på en systemrolle og udløser — kun når vægtningen faktisk ændrer sig — en
	 * gen-synkronisering af AD-medlemskaberne for hele it-systemet (issue #81).
	 *
	 * Vægtning materialiseres ingen steder; den anvendes live ved provisionering. Eneste forbruger der
	 * er afhængig af en trigger er AD-sync ({@code AdSyncApi}), som er delta-baseret og styret af
	 * {@code DirtyADGroup}-køen. Læse-API'et ({@code UserApi}) og UI'et beregner vægtning live ved hvert
	 * kald og behøver derfor ingen trigger; KSPCICS/KOMBIT/NemLogin/DMP anvender slet ikke vægtning.
	 *
	 * Hele it-systemet flagges (ikke kun JFR'er med denne systemrolle), fordi vægtning er it-system-relativ:
	 * en søskende-rolles vægt påvirker effektiviteten af alle roller i samme it-system. {@code addItSystemToQueue}
	 * er i forvejen en no-op for ikke-AD-systemer (fx SAML, der dækkes live af læse-API'et).
	 *
	 * @return den gemte systemrolle (uændret hvis vægtningen ikke ændrede sig)
	 */
	@Transactional
	public SystemRole changeWeight(SystemRole systemRole, int newWeight) {
		if (systemRole.getWeight() == newWeight) {
			return systemRole;
		}

		systemRole.setWeight(newWeight);
		SystemRole saved = systemRoleDao.save(systemRole);

		pendingADUpdateService.addItSystemToQueue(systemRole.getItSystem());

		return saved;
	}

	@Transactional
	public void delete(SystemRole systemRole) {
		systemRoleDao.delete(systemRole);
	}

	public List<SystemRole> findByItSystem(ItSystem itSystem) {
		return systemRoleDao.findByItSystem(itSystem);
	}

	// not used by actual code - only for testing purposes
	public Iterable<SystemRole> save(List<SystemRole> systemRoles) {
		return systemRoleDao.saveAll(systemRoles);
	}

	public Set<UserRole> userRolesWithSystemRole(SystemRole systemRole) {
		return userRoleService.findAllBySystemRole(systemRole);
	}

	public Set<UserRole> userRolesWithSystemRoles(Collection<SystemRole> systemRoles) {
		return userRoleService.findAllBySystemRoles(systemRoles);
	}

	public boolean isInUse(SystemRole systemRole, List<UserRole> optionalPreloadedUserRoles) {
		if (optionalPreloadedUserRoles != null) {
			for (UserRole userRole : optionalPreloadedUserRoles) {
				for (SystemRoleAssignment assignment : userRole.getSystemRoleAssignments()) {
					if (assignment.getSystemRole().getId() == systemRole.getId()) {
						return true;
					}
				}
			}
			
			return false;
		}

		return userRoleService.countBySystemRoleAssignmentsSystemRole(systemRole) > 0;
	}


	@Transactional
	public List<SystemRole> getByItSystemSystemType(ItSystemType systemType, Consumer<SystemRole> consumer) {
		List<SystemRole> result = systemRoleDao.findByItSystemSystemType(systemType);

		if (consumer != null) {
			result.forEach(consumer);
		}

		return result;
	}

	@Transactional
	public void saveAll(List<SystemRole> systemRoles) {
		systemRoleDao.saveAll(systemRoles);
	}

	@Transactional
	public void deleteAll(List<SystemRole> systemRoles) {
		systemRoleDao.deleteAll(systemRoles);
	}

	@Transactional
	public SystemRole createForRoleCatalogue(String name,  String identifier, String description, ItSystem itSystem) {
		SystemRole systemRole = new SystemRole();
		systemRole.setItSystem(itSystem);
		systemRole.setName(name);
		systemRole.setDescription(description);
		systemRole.setIdentifier(identifier);
		systemRole.setSupportedConstraintTypes(new ArrayList<>());
		return systemRoleDao.save(systemRole);
	}

	public List<SystemRole> getEffectiveSystemRoles(User user) {
		return filterByWeight(getByUserUuid(user.getUuid()));
	}

	public List<SystemRole> getEffectiveSystemRoles(User user, Collection<ItSystem> itSystems) {
		if (itSystems.isEmpty()) {
			return List.of();
		}
		Set<Long> itSystemIds = itSystems.stream().map(ItSystem::getId).collect(Collectors.toSet());
		return filterByWeight(getByUserUuidAndItSystemIds(user.getUuid(), itSystemIds));
	}

	public List<SystemRole> filterByWeight(List<SystemRole> systemRoles) {
		Map<Long, Integer> maxWeightByItSystem = new HashMap<>();
		for (SystemRole sr : systemRoles) {
			maxWeightByItSystem.merge(sr.getItSystem().getId(), sr.getWeight(), Integer::max);
		}
		return systemRoles.stream()
			.filter(sr -> sr.getWeight() >= maxWeightByItSystem.get(sr.getItSystem().getId()))
			.toList();
	}

	@Transactional
	public void notifyMaximumAssignments() {
		for (SystemRole systemRole : systemRoleDao.findByMaximumAssignmentsNotNull()) {
			Set<UserRole> userRoles = userRoleService.findAllBySystemRole(systemRole);
			List<CurrentAssignment> allAssignments = new ArrayList<>();
			for (UserRole userRole : userRoles) {
				allAssignments.addAll(assignmentService.getActiveByUserRole(userRole));
			}

			Map<User, List<CurrentAssignment>> assignmentsByUser = allAssignments.stream()
				.collect(Collectors.groupingBy(CurrentAssignment::getUser));

			long count = 0;

			for (Map.Entry<User, List<CurrentAssignment>> entry : assignmentsByUser.entrySet()) {
				List<CurrentAssignment> userAssignments = entry.getValue();
				User user = entry.getKey();
				Set<CurrentAssignment> allUserAssignments = assignmentService.getByUser(user);

				boolean hasActiveRole = userAssignments.stream()
					.anyMatch(a -> !isIneffectiveDueToWeight(a, allUserAssignments));

				if (hasActiveRole) {
					count++;
				}
			}

			if (count <= systemRole.getMaximumAssignments()) {
				continue;
			}

			if (!StringUtils.hasLength(systemRole.getItSystem().getNotificationEmail())) {
				log.warn("Maximum system role assignment mail for " + systemRole.getName() + " will not be sent. NotificationEmail on itsystem: " + systemRole.getItSystem().getName() + " is null");
				continue;
			}

			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.SYSTEM_ROLE_EXCEEDED_MAX_ASSIGNMENTS);
			if (template.isEnabled()) {
				String title = template.getTitle();
				title = title.replace(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER.getPlaceholder(), systemRole.getItSystem().getName());
				title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), systemRole.getName());
				title = title.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Long.toString(count));
				title = title.replace(EmailTemplatePlaceholder.MAX_COUNT_PLACEHOLDER.getPlaceholder(), Long.toString(systemRole.getMaximumAssignments()));

				String message = template.getMessage();
				message = message.replace(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER.getPlaceholder(), systemRole.getItSystem().getName());
				message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), systemRole.getName());
				message = message.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Long.toString(count));
				message = message.replace(EmailTemplatePlaceholder.MAX_COUNT_PLACEHOLDER.getPlaceholder(), Long.toString(systemRole.getMaximumAssignments()));

				emailQueueService.queueEmail(systemRole.getItSystem().getNotificationEmail(), title, message, template, null, null);
			}
			else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			}
		}
	}

	public boolean isIneffectiveDueToWeight(CurrentAssignment assignment, Set<CurrentAssignment> assignments) {
		if (assignment == null || assignment.getUserRole() == null) {
			return false;
		}

		int thisWeight = userRoleService.highestSystemRolesWeight(assignment.getUserRole());

		return assignments.stream()
			.filter(a -> !a.getId().equals(assignment.getId()))
			.filter(CurrentAssignment::isActive)
			.filter(a -> a.getUserRole() != null)
			.filter(a -> a.getItSystem() != null && assignment.getItSystem() != null
				&& a.getItSystem().getId() == assignment.getItSystem().getId())
			.map(a -> userRoleService.highestSystemRolesWeight(a.getUserRole()))
			.anyMatch(w -> w > thisWeight);
	}
}
