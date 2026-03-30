package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedUsers {

	@Value("${rc.dev.users-per-orgunit:5}")
	private int usersPerOrgUnit;

	private final UserDao userDao;
	private final OrgUnitService orgUnitService;
	private final DomainService domainService;

	public void seed(Map<String, String> orgUnitUuidsByName, List<Title> titles) {
		log.info("Seeding users...");

		Map<String, Title> titlesByName = titles.stream()
			.collect(Collectors.toMap(Title::getName, t -> t));

		for (DevDataDefinitions.PersonaDef def : DevDataDefinitions.PERSONAS) {
			List<Position> positions = def.positions().stream()
				.map(p -> buildPosition(p, orgUnitUuidsByName, titlesByName))
				.toList();
			saveUser(def.name(), def.userId(), positions);
		}

		for (DevDataDefinitions.ManagerAssignment ma : DevDataDefinitions.MANAGER_ASSIGNMENTS) {
			String ouUuid = orgUnitUuidsByName.get(ma.orgUnitName());
			if (ouUuid == null) {
				continue;
			}
			OrgUnit ou = orgUnitService.getByUuid(ouUuid);
			userDao.findByUserIdAndDomainAndDeletedFalse(ma.userId(), domainService.getPrimaryDomain())
				.ifPresent(manager -> {
					ou.setManager(manager);
					orgUnitService.save(ou);
				});
		}

		int i = 0;
		for (String orgUnitUuid : orgUnitUuidsByName.values()) {
			OrgUnit ou = orgUnitService.getByUuid(orgUnitUuid);
			User firstUser = null;
			for (int j = 0; j < usersPerOrgUnit; j++) {
				String userId = "bruger-" + i;
				Position position = new Position();
				position.setName("Medarbejder");
				position.setOrgUnit(ou);
				if (!titles.isEmpty() && i % 4 == 0) {
					position.setTitle(titles.get((i / 4) % titles.size()));
				}
				User saved = saveUser("Testbruger " + i, userId, List.of(position));
				if (j == 0) {
					firstUser = saved;
				}
				i++;
			}
			// Set one of the bulk users as manager, unless org unit already has a manager
			if (firstUser != null && ou.getManager() == null) {
				ou.setManager(firstUser);
				orgUnitService.save(ou);
			}
		}
	}

	private Position buildPosition(DevDataDefinitions.PositionDef def, Map<String, String> orgUnitUuidsByName,
			Map<String, Title> titlesByName) {
		String uuid = orgUnitUuidsByName.get(def.orgUnitName());
		if (uuid == null) {
			throw new IllegalStateException("OrgUnit not found: " + def.orgUnitName());
		}
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		Position position = new Position();
		position.setName(def.name());
		position.setOrgUnit(ou);
		if (def.titleName() != null) {
			Title title = titlesByName.get(def.titleName());
			if (title == null) {
				throw new IllegalStateException("Title not found: " + def.titleName());
			}
			position.setTitle(title);
		}
		return position;
	}

	private User saveUser(String name, String userId, List<Position> positions) {
		User user = new User();
		user.setUuid(UUID.randomUUID().toString());
		user.setExtUuid(UUID.randomUUID().toString());
		user.setName(name);
		user.setUserId(userId);
		user.setDomain(domainService.getPrimaryDomain());
		user.setPositions(new ArrayList<>(positions));
		positions.forEach(p -> p.setUser(user));
		return userDao.save(user);
	}

}
