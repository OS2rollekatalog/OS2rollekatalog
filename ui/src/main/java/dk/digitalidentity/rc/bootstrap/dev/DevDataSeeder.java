package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.service.SettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

@Slf4j
@Component
@Profile("dev & !test")
@RequiredArgsConstructor
public class DevDataSeeder {

	private final SettingsService settingsService;
	private final DevSeedItSystems devSeedItSystems;
	private final DevSeedOrganisation devSeedOrganisation;
	private final DevSeedTitles devSeedTitles;
	private final DevSeedUsers devSeedUsers;
	private final DevSeedRoles devSeedRoles;
	private final DevSeedRoleGroups devSeedRoleGroups;
	private final DevSeedAssignments devSeedAssignments;

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional(rollbackFor = Exception.class)
	public void seedAll() {
		log.info("Dev bootstrap starting...");
		StopWatch sw = new StopWatch("Dev bootstrap");

		sw.start("IT systems");
		DevSeedItSystems.SeedResult itSystems = devSeedItSystems.seed();
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		sw.start("Organisation");
		DevSeedOrganisation.SeedResult org = devSeedOrganisation.seed();
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		sw.start("Titles");
		DevSeedTitles.SeedResult titles = devSeedTitles.seed();
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		sw.start("Users");
		devSeedUsers.seed(org.orgUnitUuidsByName(), titles.titles());
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		sw.start("Roles");
		devSeedRoles.seed(itSystems.systemRoleIdsByIdentifier());
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		sw.start("Role groups");
		devSeedRoleGroups.seed(itSystems);
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		sw.start("Assignments");
		devSeedAssignments.seed();
		entityManager.flush();
		entityManager.clear();
		sw.stop();

		settingsService.setDevDataSeeded(true);

		log.info("Dev bootstrap complete: {} seconds", sw.getTotalTimeSeconds());
	}

}
