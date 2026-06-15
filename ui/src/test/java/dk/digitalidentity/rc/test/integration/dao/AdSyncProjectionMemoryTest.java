package dk.digitalidentity.rc.test.integration.dao;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentSmallProjection;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.event.AssignmentChangeEventHandlerService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression-vagt for AD-sync-pathen.
 * <p>
 * Verificerer at {@code findActiveAssignedAsProjection} undgår at hydrere fulde entiteter
 * via Hibernate-statistics. Baseline-query'en (full-entity {@code findActiveAssigned})
 * indlæser ÉN entitet pr. række og lazy-loader User+Domain når man tilgår dem; projection
 * indlæser nul entiteter (kun JPQL-baseret SELECT med scalars).
 * <p>
 * Beskytter mod at fremtidige ændringer (fx accidentielt at tilføje EAGER-fetch eller at
 * bytte tilbage til {@code findActiveAssigned}) reintroducerer OOM-mønstret fra 6/5 2026,
 * hvor 190k fulde {@code CurrentAssignment}-entiteter blev indlæst i én tx.
 * <p>
 * Bruger entity-load-count i stedet for heap-måling fordi heap-deltaer er for noisy ved
 * små test-datasæt — direction er rigtig (projection bruger mindre), men signal er for lavt
 * til at sætte robuste tærskler. Entity-count er deterministisk: 0 vs N.
 */
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdSyncProjectionMemoryTest extends BaseIntegrationTest {

	private static final int USER_ROLES = 30;
	private static final int USERS = 100;
	private static final int ROLES_PER_USER = 5;

	private final CurrentAssignmentDao currentAssignmentDao;
	private final AssignmentChangeEventHandlerService recalcService;

	@Autowired
	private dk.digitalidentity.rc.dao.DomainDao domainDao;

	@Test
	@DisplayName("projection indlæser ZERO CurrentAssignment-entiteter, full-entity query indlæser N")
	void projectionLoadsNoEntities() {
		Seed seed = seedAdLikeData();
		flushAndClear();

		Statistics stats = entityManager.getEntityManagerFactory()
			.unwrap(SessionFactory.class)
			.getStatistics();
		stats.setStatisticsEnabled(true);
		LocalDate now = LocalDate.now();

		// --- Baseline: full entity query ---
		stats.clear();
		Set<CurrentAssignment> full = currentAssignmentDao.findActiveAssigned(seed.userRoles, now);
		// Tving lazy-init af de felter AD-sync ville bruge så de bliver loaded
		long fullWalk = full.stream()
			.mapToLong(ca -> ca.getUser().getUserId().length() + ca.getUserRole().getId() + ca.getUser().getDomain().getId())
			.sum();
		long fullEntityLoads = stats.getEntityLoadCount();
		long fullSize = full.size();
		flushAndClear();

		// --- Projection ---
		stats.clear();
		Set<CurrentAssignmentSmallProjection> proj = currentAssignmentDao.findActiveAssignedAsProjection(seed.userRoles, now);
		long projWalk = proj.stream()
			.mapToLong(p -> p.getUserId().length() + p.getUserRoleId() + p.getUserDomainId())
			.sum();
		long projEntityLoads = stats.getEntityLoadCount();
		long projSize = proj.size();

		System.out.printf("%n=== AD-sync projection vs full-entity ===%n" +
				"  Rækker:                   %d (begge)%n" +
				"  Entity-loads (full):      %d%n" +
				"  Entity-loads (projection): %d%n" +
				"  fakeWork:                 %d / %d%n%n",
			fullSize, fullEntityLoads, projEntityLoads, fullWalk, projWalk
		);

		// Korrekthed: samme antal rækker
		assertThat(projSize).as("samme rækkemængde").isEqualTo(fullSize);

		// Regression-vagt: full-entity loader entiteter, projection ikke
		assertThat(fullEntityLoads)
			.as("baseline-query loader CurrentAssignment + User + Domain pr. række")
			.isGreaterThan(0);

		assertThat(projEntityLoads)
			.as("projection skal IKKE indlæse entiteter — kun scalars. Hvis dette tal er > 0, " +
				"er projektionen revertet til full-entity, og OOM-mønstret fra 6/5 kommer tilbage.")
			.isZero();
	}

	@Test
	@DisplayName("projection returnerer samme (userId, userRoleId)-par som full entity")
	void projectionReturnsSameData() {
		Seed seed = seedAdLikeData();
		flushAndClear();

		LocalDate now = LocalDate.now();

		Set<String> fullPairs = new HashSet<>();
		currentAssignmentDao.findActiveAssigned(seed.userRoles, now)
			.forEach(ca -> fullPairs.add(ca.getUser().getUserId() + "/" + ca.getUserRole().getId()));

		Set<String> projPairs = new HashSet<>();
		currentAssignmentDao.findActiveAssignedAsProjection(seed.userRoles, now)
			.forEach(p -> projPairs.add(p.getUserId() + "/" + p.getUserRoleId()));

		assertThat(projPairs).isEqualTo(fullPairs);
	}

	// --- Seed ---

	private record Seed(Set<UserRole> userRoles, ItSystem itSystem) {}

	private Seed seedAdLikeData() {
		Domain domain = domainDao.findByName("Administrativt");

		ItSystem itSystem = new ItSystem();
		itSystem.setUuid(UUID.randomUUID().toString());
		itSystem.setName("AD-like System");
		itSystem.setIdentifier("ad-like");
		itSystem.setSystemType(ItSystemType.AD);
		itSystem.setLastUpdated(new Date());
		itSystem.setDomain(domain);
		entityManager.persist(itSystem);

		Set<UserRole> userRoles = new HashSet<>();
		java.util.List<UserRole> userRoleList = new ArrayList<>();
		for (int i = 0; i < USER_ROLES; i++) {
			UserRole ur = new UserRole();
			ur.setUuid(UUID.randomUUID().toString());
			ur.setName("AD UR " + i);
			ur.setIdentifier("ad-ur-" + i);
			ur.setItSystem(itSystem);
			ur.setSystemRoleLinkType(SystemRoleLinkType.NONE);
			ur.setSystemRoleAssignments(new ArrayList<>());
			ur.setDescription("AD role " + i);
			entityManager.persist(ur);

			SystemRole sr = new SystemRole();
			sr.setUuid(UUID.randomUUID().toString());
			sr.setName("AD SR " + i);
			sr.setIdentifier("ad-sr-" + i);
			sr.setItSystem(itSystem);
			sr.setRoleType(RoleType.BOTH);
			sr.setWeight(1);
			entityManager.persist(sr);

			SystemRoleAssignment sra = new SystemRoleAssignment();
			sra.setUserRole(ur);
			sra.setSystemRole(sr);
			sra.setAssignedByUserId("test");
			sra.setAssignedByName("Repro");
			sra.setAssignedTimestamp(new Date());
			sra.setConstraintValues(new ArrayList<>());
			ur.getSystemRoleAssignments().add(sra);
			entityManager.persist(sra);

			userRoles.add(ur);
			userRoleList.add(ur);
		}

		java.util.Random random = new java.util.Random(42);
		java.util.List<String> userUuids = new ArrayList<>();
		for (int u = 0; u < USERS; u++) {
			User user = new User();
			user.setUuid("ad-user-" + u);
			user.setUserId("ad-uid-" + u);
			user.setName("AD User " + u);
			user.setExtUuid(UUID.randomUUID().toString());
			user.setDomain(domain);
			user.setPositions(new ArrayList<>());
			user.setUserRoleAssignments(new ArrayList<>());
			user.setRoleGroupAssignments(new ArrayList<>());
			user.setAltAccounts(new ArrayList<>());
			user.setKles(new ArrayList<>());
			user.setManagerSubstitutes(new ArrayList<>());
			user.setSubstituteFor(new ArrayList<>());
			user.setFunctionAssignments(new ArrayList<>());
			entityManager.persist(user);
			userUuids.add(user.getUuid());

			Set<Integer> picked = new HashSet<>();
			while (picked.size() < ROLES_PER_USER) {
				picked.add(random.nextInt(USER_ROLES));
			}
			for (int idx : picked) {
				UserRole ur = userRoleList.get(idx);
				UserUserRoleAssignment uura = new UserUserRoleAssignment();
				uura.setUser(user);
				uura.setUserRole(ur);
				uura.setAssignedByUserId("test");
				uura.setAssignedByName("Repro");
				uura.setAssignedTimestamp(new Date());
				uura.setPostponedConstraints(new ArrayList<>());
				entityManager.persist(uura);
				user.getUserRoleAssignments().add(uura);
			}
		}

		entityManager.flush();

		// Trigger recalc så current_assignment populeres
		int batch = 25;
		for (int i = 0; i < userUuids.size(); i += batch) {
			Set<String> chunk = new HashSet<>(userUuids.subList(i, Math.min(i + batch, userUuids.size())));
			recalcService.updateUsers(chunk);
			entityManager.flush();
			entityManager.clear();
		}

		return new Seed(userRoles, itSystem);
	}
}
