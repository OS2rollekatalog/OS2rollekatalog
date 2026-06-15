package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystemAttestationResponsible;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedAssignments {

	private final UserDao userDao;
	private final UserRoleDao userRoleDao;
	private final DomainService domainService;
	private final ItSystemService itSystemService;
	private final UserService userService;

	public void seed() {
		log.info("Seeding user role assignments...");

		// Role keys defined in PersonaDef → resolved UserRole
		Map<String, UserRole> rolesByKey = Map.of(
			"administrator", userRoleDao.getByIdentifier("administrator"),
			"assigner",      userRoleDao.getByIdentifier("tildeler"),
			"read-only",     userRoleDao.getByIdentifier("readonly")
		);

		List<String> userIds = DevDataDefinitions.PERSONAS.stream()
			.filter(p -> !p.roleKeys().isEmpty())
			.map(DevDataDefinitions.PersonaDef::userId)
			.distinct()
			.toList();

		Map<String, User> usersById = userDao.findByUserIdInAndDomainAndDeletedFalse(userIds, domainService.getPrimaryDomain())
			.stream()
			.collect(Collectors.toMap(User::getUserId, Function.identity()));

		LinkedHashSet<User> modifiedUsers = new LinkedHashSet<>();

		for (DevDataDefinitions.PersonaDef persona : DevDataDefinitions.PERSONAS) {
			if (persona.roleKeys().isEmpty()) {
				continue;
			}
			User user = usersById.get(persona.userId());
			if (user == null) {
				log.warn("Skipping assignment for unknown user: {}", persona.userId());
				continue;
			}
			for (String roleKey : persona.roleKeys()) {
				UserRole role = rolesByKey.get(roleKey);
				if (role == null) {
					log.warn("Unknown role key '{}' for user '{}' — skipping", roleKey, persona.userId());
					continue;
				}
				UserUserRoleAssignment assignment = new UserUserRoleAssignment();
				assignment.setUser(user);
				assignment.setUserRole(role);
				assignment.setAssignedByName("Development Bootstrapper");
				assignment.setAssignedByUserId("dev-bootstrapper");
				assignment.setAssignedTimestamp(new Date());
				user.getUserRoleAssignments().add(assignment);
				modifiedUsers.add(user);
			}
		}

		userDao.saveAll(modifiedUsers);
		modifiedUsers.forEach(u -> userService.queueForRecalculation(u));

		User responsible = usersById.get(DevDataDefinitions.IT_SYSTEM_RESPONSIBLE_USER_ID);
		if (responsible != null) {
			itSystemService.getAll().forEach(system -> system.getAttestationResponsibles().add(ItSystemAttestationResponsible.builder().itSystem(system).user(responsible).build()));
		} else {
			log.warn("IT system responsible user '{}' not found — skipping",
				DevDataDefinitions.IT_SYSTEM_RESPONSIBLE_USER_ID);
		}
	}

}
