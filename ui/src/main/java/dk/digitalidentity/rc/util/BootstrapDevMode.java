package dk.digitalidentity.rc.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.model.ManagerDTO;
import dk.digitalidentity.rc.controller.api.model.OrgUnitDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationDTO;
import dk.digitalidentity.rc.controller.api.model.PositionDTO;
import dk.digitalidentity.rc.controller.api.model.UserDTO;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.OrganisationImporter;
import dk.digitalidentity.rc.service.SystemRoleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.time.StopWatch;

@Transactional(rollbackFor = Exception.class)
@Component
@EnableScheduling
@Slf4j
public class BootstrapDevMode {
	// two "hardcoded" UUIDs that can be referenced by the tests in our code
	public static final String orgUnitUUID = UUID.randomUUID().toString();
	public static final String userUUID = UUID.randomUUID().toString();

	@Value("${environment.dev:false}")
	private boolean devEnvironment;

	@Autowired
	private OrganisationImporter orgImporter;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private UserRoleDao userRoleDao;

	@Autowired
	private UserDao userDao;

	@Autowired
	private TitleDao titleDao;
	
	@Autowired
	private RoleGroupDao roleGroupDao;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private DomainService domainService;

    @PersistenceContext
    private EntityManager entityManager;

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() throws Exception {
		if (configuration.getScheduled().isEnabled()) {
			init(false);
		}
	}
	
	public void init(boolean force) throws Exception {
		// do not bootstrap if this is production
		if (!devEnvironment) {
			return;
		}

        entityManager.setFlushMode(FlushModeType.COMMIT);

        var stopWatch = new StopWatch();
        stopWatch.start();
		// do not bootstrap if there is already data in the system
		if (orgUnitService.getRoot() == null) {
			createTitles();
			
			List<ItSystem> itSystems = createItSystems();

			List<UserRole> userRoles = createUserRoles(itSystems);

			createRoleGroups(userRoles);

			importOrganisation();
			
			findUserOneAndMakeHimAdmin();

			setItSystemResponsible();
		}


		// when running tests (and only when running tests), we need
		// at least one user with all roles assigned
		if (force) {
			assignRoles();
		}
        stopWatch.stop();

        log.info("Init finished: " + stopWatch.getTime(TimeUnit.SECONDS) + " seconds" );
	}
	
	private void createTitles() {
        for(int i = 0; i < 100; i++) {
            var title = new Title();
            title.setActive(true);
            title.setName("Title" + i);
            title.setUuid(UUID.randomUUID().toString());
            titleDao.save(title);
        }
	}

	private void findUserOneAndMakeHimAdmin() {
		User rolunittest01 = userDao.findByUserIdAndDomainAndDeletedFalse("rolunittest01", domainService.getPrimaryDomain()).orElseThrow();
		User bsg = userDao.findByUserIdAndDomainAndDeletedFalse("bsg", domainService.getPrimaryDomain()).orElseThrow();
		User kbp = userDao.findByUserIdAndDomainAndDeletedFalse("kbp", domainService.getPrimaryDomain()).orElseThrow();
		User and = userDao.findByUserIdAndDomainAndDeletedFalse("and", domainService.getPrimaryDomain()).orElseThrow();
		User skr = userDao.findByUserIdAndDomainAndDeletedFalse("skr", domainService.getPrimaryDomain()).orElseThrow();
		User jls = userDao.findByUserIdAndDomainAndDeletedFalse("jls", domainService.getPrimaryDomain()).orElseThrow();
		User psu = userDao.findByUserIdAndDomainAndDeletedFalse("psu", domainService.getPrimaryDomain()).orElseThrow();
		UserRole administrator = userRoleDao.getByIdentifier("administrator");
		
		UserUserRoleAssignment assignment = new UserUserRoleAssignment();
		assignment.setUser(rolunittest01);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);

		rolunittest01.getUserRoleAssignments().add(assignment);
		userDao.save(rolunittest01);
		
		assignment = new UserUserRoleAssignment();
		assignment.setUser(bsg);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);

		bsg.getUserRoleAssignments().add(assignment);
		userDao.save(bsg);

		assignment = new UserUserRoleAssignment();
		assignment.setUser(kbp);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);

		kbp.getUserRoleAssignments().add(assignment);
		userDao.save(kbp);
		
		assignment = new UserUserRoleAssignment();
		assignment.setUser(and);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);

		and.getUserRoleAssignments().add(assignment);
		userDao.save(and);

		assignment = new UserUserRoleAssignment();
		assignment.setUser(skr);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);

		skr.getUserRoleAssignments().add(assignment);
		userDao.save(skr);

		assignment = new UserUserRoleAssignment();
		assignment.setUser(jls);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);

		jls.getUserRoleAssignments().add(assignment);
		userDao.save(jls);

		assignment = new UserUserRoleAssignment();
		assignment.setUser(psu);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);
		
		psu.getUserRoleAssignments().add(assignment);
		userDao.save(psu);
	}

	private void setItSystemResponsible() {
		User bsg = userDao.findByUserIdAndDomainAndDeletedFalse("bsg", domainService.getPrimaryDomain()).orElseThrow();
		itSystemService.getAll()
				.forEach(system -> {
					system.setAttestationResponsible(bsg);
				});
	}

	private void importOrganisation() throws Exception {
		List<OrgUnitDTO> orgUnits = new ArrayList<>();
		List<UserDTO> users = new ArrayList<>();

		OrgUnitDTO kommune = createOU(orgUnits, "Hørning Kommune");
		OrgUnitDTO job = createOU(orgUnits, "Job og beskæftigelse");
		OrgUnitDTO arbejdsmarked = createOU(orgUnits, "Arbejdsmarkedsområdet");
		OrgUnitDTO jobcenter = createOU(orgUnits, "Jobcenteret");
		OrgUnitDTO bakkeskolen = createOU(orgUnits, "Bakkeskolen");
		OrgUnitDTO aaskolen = createOU(orgUnits, "Aaskolen");
		OrgUnitDTO skole = createOU(orgUnits, "Børn og skole");
		OrgUnitDTO landbrug = createOU(orgUnits, "Landbrug og natur");
		OrgUnitDTO kultur = createOU(orgUnits, "Kultur og fritid");
		OrgUnitDTO miljoe = createOU(orgUnits, "Teknik og miljø");
		OrgUnitDTO teknik = createOU(orgUnits, "Teknik og kultur");

		jobcenter.setUuid(orgUnitUUID);

		job.setParentOrgUnitUuid(kommune.getUuid());
		skole.setParentOrgUnitUuid(kommune.getUuid());
		teknik.setParentOrgUnitUuid(kommune.getUuid());
		arbejdsmarked.setParentOrgUnitUuid(job.getUuid());
		jobcenter.setParentOrgUnitUuid(job.getUuid());
		bakkeskolen.setParentOrgUnitUuid(skole.getUuid());
		aaskolen.setParentOrgUnitUuid(skole.getUuid());
		kultur.setParentOrgUnitUuid(teknik.getUuid());
		miljoe.setParentOrgUnitUuid(teknik.getUuid());
		landbrug.setParentOrgUnitUuid(miljoe.getUuid());

		PositionDTO p = new PositionDTO();

        String[] positions_lut = {
            "Sagsbehandler",
            "Skolelæreinde",
            "Forstanderinde",
            "Sekretær",
            "Alt-mulig-mand",
            "Tester",
            "Borgmester",
            "Udvikler"
        };

        var titles      = titleDao.findAll();
        var title_it    = titles.iterator();
        var title_lut   = new Title[100];
        for(int i = 0; i < 100; i++) {
            title_lut[i] = title_it.next();
        }

        // TODO: Dont spam console with users
        var user_amount = 5000;
        var rand        = new Random();
        for(int i = 0; i < user_amount; i++) {
            var user = createUser(users, "Bruger " + i, "b" + i);
            user.setUuid(UUID.randomUUID().toString());

            // Generate random amount of positions(1-3)
            for(var num = 0; num < rand.nextInt(1, 3); num++) {
                p = new PositionDTO();
                p.setName(positions_lut[rand.nextInt(8)]);
                p.setOrgUnitUuid(orgUnits.get(rand.nextInt(orgUnits.size())).getUuid());

                // Give 30% of positions a title
                if(rand.nextInt(10) <= 2) {
                    var title_num = rand.nextBoolean() ? 10 : 100;
                    p.setTitleUuid(title_lut[rand.nextInt(title_num)].getUuid());
                }

                user.getPositions().add(p);
            }
        }

        // Set 10 random users as manager
        for(int i = 0; i < orgUnits.size(); i++) {
            var org_unit  = orgUnits.get(i);
            var rand_user = users.get(rand.nextInt(user_amount));
            var manager   = new ManagerDTO(rand_user.getExtUuid(), rand_user.getUserId());

            org_unit.setManager(manager);
        }

		UserDTO bente = createUser(users, "Bente Børgesen", "bbog");
		p = new PositionDTO();
		p.setName("Sagsbehandler");
		p.setOrgUnitUuid(arbejdsmarked.getUuid());
		bente.getPositions().add(p);
		bente.setExtUuid(userUUID);

		UserDTO frederikke = createUser(users, "Frederikke Urgdal", "fnur");
		p = new PositionDTO();
		p.setName("Skolelæreinde");
		p.setOrgUnitUuid(bakkeskolen.getUuid());
		p.setTitleUuid(titleDao.findAll().getFirst().getUuid());
		frederikke.getPositions().add(p);
		p = new PositionDTO();
		p.setName("Skolelæreinde");
		p.setOrgUnitUuid(aaskolen.getUuid());
		p.setTitleUuid(titleDao.findAll().getLast().getUuid());
		frederikke.getPositions().add(p);

		UserDTO freja = createUser(users, "Freja Irkeda", "fnui");
		p = new PositionDTO();
		p.setName("Forstanderinde");
		p.setOrgUnitUuid(bakkeskolen.getUuid());
		freja.getPositions().add(p);

		UserDTO gert = createUser(users, "Gert Grinte-Waldorfsen", "ggri");
		p = new PositionDTO();
		p.setName("Sekretær");
		p.setOrgUnitUuid(kommune.getUuid());
		gert.getPositions().add(p);

		UserDTO rolunittest01 = createUser(users, "Justin McCase", "rolunittest01");
		p = new PositionDTO();
		p.setName("Alt-mulig-mand");
		p.setOrgUnitUuid(kommune.getUuid());
		rolunittest01.getPositions().add(p);

		UserDTO bsg = createUser(users, "Brian Tester", "bsg");
		p = new PositionDTO();
		p.setName("Tester");
		p.setOrgUnitUuid(kommune.getUuid());
		bsg.getPositions().add(p);

		UserDTO kbp = createUser(users, "Kaspar Tester", "kbp");
		p = new PositionDTO();
		p.setName("Tester");
		p.setOrgUnitUuid(kommune.getUuid());
		kbp.getPositions().add(p);
		
		UserDTO and = createUser(users, "Andreas Duffy", "and");
		p = new PositionDTO();
		p.setName("p");
		p.setOrgUnitUuid(kommune.getUuid());
		and.getPositions().add(p);

		UserDTO viggo = createUser(users, "Viggo Mortensen", "vmort");
		p = new PositionDTO();
		p.setName("Borgmester");
		p.setOrgUnitUuid(kommune.getUuid());
		viggo.getPositions().add(p);

		UserDTO skr = createUser(users, "Sune Koch Rønnow", "skr");
		p = new PositionDTO();
		p.setName("Udvikler");
		p.setOrgUnitUuid(kommune.getUuid());
		p.setTitleUuid(titleDao.findAll().getFirst().getUuid());
		skr.getPositions().add(p);

		UserDTO jls = createUser(users, "Julius Larsen Seerup", "jls");
		p = new PositionDTO();
		p.setName("Udvikler");
		p.setOrgUnitUuid(kommune.getUuid());
		p.setTitleUuid(titleDao.findAll().getFirst().getUuid());
		jls.getPositions().add(p);

		UserDTO psu = createUser(users, "Piotr Suski", "psu");
		p = new PositionDTO();
		p.setName("Udvikler");
		p.setOrgUnitUuid(kommune.getUuid());
		p.setTitleUuid(titleDao.findAll().getFirst().getUuid());
		psu.getPositions().add(p);

		OrganisationDTO payload = new OrganisationDTO();
		payload.setOrgUnits(orgUnits);
		payload.setUsers(users);

		orgImporter.fullSync(payload, domainService.getPrimaryDomain());
	}

	private void createRoleGroups(List<UserRole> userRoles) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setDescription("description....");
		roleGroup.setName("My rolegroup");
		roleGroup.setUserRoleAssignments(new ArrayList<>());
		
		RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
		assignment.setUserRole(userRoles.get(0));
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		roleGroup.getUserRoleAssignments().add(assignment);

		assignment = new RoleGroupUserRoleAssignment();
		assignment.setUserRole(userRoles.get(1));
		assignment.setRoleGroup(roleGroup);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		roleGroup.getUserRoleAssignments().add(assignment);

		roleGroupDao.save(roleGroup);
	}

	private void assignRoles() {
		@SuppressWarnings("deprecation")
		User user = userDao.findAll().get(0);
		user.getUserRoleAssignments().clear();

		for (UserRole userRole : userRoleDao.findAll()) {
			UserUserRoleAssignment assignment = new UserUserRoleAssignment();
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			assignment.setAssignedByName("system");
			assignment.setAssignedByUserId("system");
			assignment.setAssignedTimestamp(new Date());
			if (user.getPositions() != null && user.getPositions().size() > 0) {
				assignment.setOrgUnit(user.getPositions().get(0).getOrgUnit());
			}
			user.getUserRoleAssignments().add(assignment);
		}

		userDao.save(user);
	}

	private List<UserRole> createUserRoles(List<ItSystem> itSystems) {
		List<UserRole> userRoles = new ArrayList<>();
		int i = 1;

		for (ItSystem itSystem : itSystems) {
			List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
			
			UserRole userRole = new UserRole();
			userRole.setDescription("description");
			userRole.setIdentifier(itSystem.getIdentifier() + "_" + i);
			userRole.setName(itSystem.getName() + " role " + i);
			userRole.setItSystem(itSystem);
			userRole.setSystemRoleAssignments(new ArrayList<>());
			userRole.setRoleAssignmentAttestationByAttestationResponsible(true);

			SystemRole systemRole = systemRoles.get(0);
			SystemRoleAssignment sra = new SystemRoleAssignment();
			sra.setAssignedByName("Systembruger");
			sra.setAssignedByUserId("system");
			sra.setAssignedTimestamp(new Date());
			sra.setSystemRole(systemRole);
			sra.setUserRole(userRole);
			
			if (systemRole.getSupportedConstraintTypes() != null) {
				for (ConstraintTypeSupport supportedConstraint : systemRole.getSupportedConstraintTypes()) {
					sra.setConstraintValues(new ArrayList<>());

					SystemRoleAssignmentConstraintValue constraint = new SystemRoleAssignmentConstraintValue();
					constraint.setConstraintType(supportedConstraint.getConstraintType());
					constraint.setConstraintValue("27.18.00");
					constraint.setConstraintValueType(ConstraintValueType.VALUE);
					constraint.setSystemRoleAssignment(sra);

					sra.getConstraintValues().add(constraint);
				}
			}
			
			userRole.getSystemRoleAssignments().add(sra);
			
			userRoleDao.save(userRole);

			userRoles.add(userRole);
			i++;
		}

		return userRoles;
	}

	private List<ItSystem> createItSystems() {		
		ConstraintType constraintType = new ConstraintType();
		constraintType.setEntityId(Constants.KLE_CONSTRAINT_ENTITY_ID);
		constraintType.setUuid("9366d0e0-52bd-464c-8e2f-c8b63b021e52");
		constraintType.setName("KLE");
		constraintType.setUiType(ConstraintUIType.REGEX);
		constraintType.setRegex(".*");
		constraintType = constraintTypeService.save(constraintType);
		
		ConstraintType constraintType2 = new ConstraintType();
		constraintType2.setEntityId(Constants.OU_CONSTRAINT_ENTITY_ID);
		constraintType2.setUuid("9366d0e0-52bd-464c-8e2f-c8b63b021e51");
		constraintType2.setName("Organisation");
		constraintType2.setUiType(ConstraintUIType.REGEX);
		constraintType2.setRegex(".*");
		constraintType2 = constraintTypeService.save(constraintType2);
		
		ConstraintType constraintType3 = new ConstraintType();
		constraintType3.setEntityId("https://sts.kombit.dk/constraints/itsystem/1");
		constraintType3.setUuid("9366d0e0-52bd-464c-8e2f-c8b63b021e57");
		constraintType3.setName("Organisation");
		constraintType3.setUiType(ConstraintUIType.REGEX);
		constraintType3.setRegex(".*");
		constraintType3 = constraintTypeService.save(constraintType3);

		ItSystem kombit = new ItSystem();
		kombit.setIdentifier("KOMBIT");
		kombit.setName("KOMBIT System");
		kombit.setSystemType(ItSystemType.KOMBIT);
		itSystemService.save(kombit);

		ConstraintTypeSupport constraintSupport = new ConstraintTypeSupport();
		constraintSupport.setConstraintType(constraintType);
		constraintSupport.setMandatory(false);
		
		ConstraintTypeSupport constraintSupport2 = new ConstraintTypeSupport();
		constraintSupport2.setConstraintType(constraintType2);
		constraintSupport2.setMandatory(false);
		
		ConstraintTypeSupport constraintSupport3 = new ConstraintTypeSupport();
		constraintSupport3.setConstraintType(constraintType3);
		constraintSupport3.setMandatory(false);

		SystemRole kombitRole1 = new SystemRole();
		kombitRole1.setDescription("description...");
		kombitRole1.setIdentifier("http://kombit.dk/roles/usersystemrole/se_sag/1");
		kombitRole1.setItSystem(kombit);
		kombitRole1.setName("Se sag");
		kombitRole1.setSupportedConstraintTypes(new ArrayList<>());
		kombitRole1.getSupportedConstraintTypes().add(constraintSupport);
		kombitRole1.getSupportedConstraintTypes().add(constraintSupport2);
		kombitRole1.getSupportedConstraintTypes().add(constraintSupport3);
		kombitRole1.setRoleType(RoleType.BOTH);
		systemRoleService.save(kombitRole1);

		SystemRole kombitRole2 = new SystemRole();
		kombitRole2.setDescription("description...");
		kombitRole2.setIdentifier("http://kombit.dk/roles/usersystemrole/opret_sag/1");
		kombitRole2.setItSystem(kombit);
		kombitRole2.setRoleType(RoleType.BOTH);
		kombitRole2.setName("Opret sag");
		systemRoleService.save(kombitRole2);

		ItSystem ad = new ItSystem();
		ad.setIdentifier("AD");
		ad.setName("AD");
		ad.setCanEditThroughApi(true);
		ad.setDomain(domainService.getPrimaryDomain());
		ad.setSystemType(ItSystemType.AD);
		itSystemService.save(ad);

		SystemRole adRole1 = new SystemRole();
		adRole1.setDescription("description...");
		adRole1.setIdentifier("testgroup-001");
		adRole1.setItSystem(ad);
		adRole1.setRoleType(RoleType.BOTH);
		adRole1.setName("AD Group 1");
		systemRoleService.save(adRole1);

		SystemRole adRole2 = new SystemRole();
		adRole2.setDescription("description...");
		adRole2.setIdentifier("testgroup-002");
		adRole2.setItSystem(ad);
		adRole2.setRoleType(RoleType.BOTH);
		adRole2.setName("AD Group 2");
		systemRoleService.save(adRole2);

		SystemRole adRole3 = new SystemRole();
		adRole3.setDescription("description...");
		adRole3.setIdentifier("testgroup-003");
		adRole3.setItSystem(ad);
		adRole3.setRoleType(RoleType.BOTH);
		adRole3.setName("AD Group 3");
		systemRoleService.save(adRole3);

		SystemRole adRole4 = new SystemRole();
		adRole4.setDescription("description...");
		adRole4.setIdentifier("testgroup-004");
		adRole4.setItSystem(ad);
		adRole4.setRoleType(RoleType.BOTH);
		adRole4.setName("AD Group 4");
		systemRoleService.save(adRole4);

		SystemRole adRole5 = new SystemRole();
		adRole5.setDescription("description...");
		adRole5.setIdentifier("testgroup-005");
		adRole5.setItSystem(ad);
		adRole5.setRoleType(RoleType.BOTH);
		adRole5.setName("AD Group 5");
		systemRoleService.save(adRole5);

		List<ItSystem> itSystems = new ArrayList<>();
		itSystems.add(ad);
		itSystems.add(kombit);

		return itSystems;
	}

	private OrgUnitDTO createOU(List<OrgUnitDTO> orgUnits, String name) {
		OrgUnitDTO ou = new OrgUnitDTO();
		ou.setName(name);
		ou.setUuid(UUID.randomUUID().toString());

		orgUnits.add(ou);

		return ou;
	}

	private UserDTO createUser(List<UserDTO> users, String name, String userId) {
		UserDTO user = new UserDTO();
		user.setName(name);
		user.setPositions(new ArrayList<>());
		user.setUserId(userId);
		user.setExtUuid(UUID.randomUUID().toString());

		users.add(user);

		return user;
	}
}
