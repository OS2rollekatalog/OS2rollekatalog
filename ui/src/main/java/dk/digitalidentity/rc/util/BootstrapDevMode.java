package dk.digitalidentity.rc.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.controller.api.model.OrgUnitAM;
import dk.digitalidentity.rc.controller.api.model.UserAM;
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
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.OrganisationImporterOld;
import dk.digitalidentity.rc.service.SystemRoleService;

@Transactional(rollbackFor = Exception.class)
@Component
@EnableScheduling
public class BootstrapDevMode {
	// two "hardcoded" UUIDs that can be referenced by the tests in our code
	public static final String orgUnitUUID = UUID.randomUUID().toString();
	public static final String userUUID = UUID.randomUUID().toString();

	@Value("${environment.dev:false}")
	private boolean devEnvironment;

	@Autowired
	private OrganisationImporterOld orgImporter;

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
	
	// run every 8 hours, but wait 60 seconds after boot... kinda need a run-once
	// here instead ;)
	@Scheduled(fixedDelay = 8 * 60 * 60 * 1000, initialDelay = 60000)
	public void init() throws Exception {
		init(false);
	}
	
	public void init(boolean force) throws Exception {
		// do not bootstrap if this is production
		if (!devEnvironment) {
			return;
		}
		
		// do not bootstrap if there is already data in the system
		if (orgUnitService.getRoot() == null) {
			createTitles();
			
			List<ItSystem> itSystems = createItSystems();

			List<UserRole> userRoles = createUserRoles(itSystems);

			createRoleGroups(userRoles);

			importOrganisation();
			
			findUserOneAndMakeHimAdmin();
		}
		
		// when running tests (and only when running tests), we need
		// at least one user with all roles assigned
		if (force) {
			assignRoles();
		}
	}
	
	private void createTitles() {
		Title title = new Title();
		title.setActive(true);
		title.setName("Title One");
		title.setUuid(UUID.randomUUID().toString());
		titleDao.save(title);

		title = new Title();
		title.setActive(true);
		title.setName("Title Two");
		title.setUuid(UUID.randomUUID().toString());
		titleDao.save(title);
	}

	private void findUserOneAndMakeHimAdmin() {
		User user1 = userDao.getByUserIdAndActiveTrue("user1");
		User bsg = userDao.getByUserIdAndActiveTrue("bsg");
		UserRole administrator = userRoleDao.getByIdentifier("administrator");
		
		UserUserRoleAssignment assignment = new UserUserRoleAssignment();
		assignment.setUser(user1);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);
		
		user1.getUserRoleAssignments().add(assignment);
		userDao.save(user1);
		
		assignment = new UserUserRoleAssignment();
		assignment.setUser(bsg);
		assignment.setAssignedByName("Systembruger");
		assignment.setAssignedByUserId("system");
		assignment.setAssignedTimestamp(new Date());
		assignment.setUserRole(administrator);
		
		bsg.getUserRoleAssignments().add(assignment);
		userDao.save(bsg);
	}

	private void importOrganisation() throws Exception {
		OrgUnitAM arbejdsmarked = createOU("Arbejdsmarkedsområdet");
		OrgUnitAM jobcenter = createOU("Jobcenteret");
		jobcenter.setUuid(orgUnitUUID);

		OrgUnitAM job = createOU("Job og beskæftigelse");
		job.getChildren().add(arbejdsmarked);
		job.getChildren().add(jobcenter);

		OrgUnitAM bakkeskolen = createOU("Bakkeskolen");
		OrgUnitAM aaskolen = createOU("Aaskolen");

		OrgUnitAM skole = createOU("Børn og skole");
		skole.getChildren().add(bakkeskolen);
		skole.getChildren().add(aaskolen);

		OrgUnitAM landbrug = createOU("Landbrug og natur");
		OrgUnitAM kultur = createOU("Kultur og fritid");
		OrgUnitAM miljoe = createOU("Teknik og miljø");
		miljoe.getChildren().add(landbrug);

		OrgUnitAM teknik = createOU("Teknik og kultur");
		teknik.getChildren().add(kultur);
		teknik.getChildren().add(miljoe);

		OrgUnitAM kommune = createOU("Hørning Kommune");
		kommune.getChildren().add(job);
		kommune.getChildren().add(skole);
		kommune.getChildren().add(teknik);

		UserAM bente = createUser("Bente Børgesen", "Sagsbehandler", "bbog");
		UserAM frederikke = createUser("Frederikke Urgdal", "Skolelæreinde", "fnur");
		UserAM freja = createUser("Freja Irkeda", "Forstanderinde", "fnui");
		UserAM gert = createUser("Gert Grinte-Waldorfsen", "Sekretær", "ggri");
		UserAM user1 = createUser("Justin McCase", "Alt-mulig-mand", "user1");
		UserAM bsg = createUser("Brian Tester", "Tester", "bsg");
		UserAM viggo = createUser("Viggo Mortensen", "Borgmester", "vmort");

		bente.setUuid(userUUID);
		
		// clone Frederikke for 2nd employment
		UserAM frederikke2 = createUser("Frederikke Urgdal", "Vikar", "fnur");
		frederikke2.setUuid(frederikke.getUuid());

		bakkeskolen.getEmployees().add(frederikke);
		kommune.getEmployees().add(frederikke2);
		kommune.getEmployees().add(viggo);
		arbejdsmarked.getEmployees().add(bente);
		arbejdsmarked.getEmployees().add(bsg);
		jobcenter.getEmployees().add(user1);
		arbejdsmarked.getEmployees().add(gert);
		aaskolen.getEmployees().add(freja);

		orgImporter.bigImport(kommune);
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
		constraintType.setEntityId("http://sts.kombit.dk/constraint/kle/1");
		constraintType.setUuid("9366d0e0-52bd-464c-8e2f-c8b63b021e52");
		constraintType.setName("KLE");
		constraintType.setUiType(ConstraintUIType.REGEX);
		constraintType.setRegex(".");
		constraintType = constraintTypeService.save(constraintType);

		ItSystem kombit = new ItSystem();
		kombit.setIdentifier("KOMBIT");
		kombit.setName("KOMBIT System");
		kombit.setSystemType(ItSystemType.SAML);
		itSystemService.save(kombit);

		ConstraintTypeSupport constraintSupport = new ConstraintTypeSupport();
		constraintSupport.setConstraintType(constraintType);
		constraintSupport.setMandatory(false);

		SystemRole kombitRole1 = new SystemRole();
		kombitRole1.setDescription("description...");
		kombitRole1.setIdentifier("http://kombit.dk/roles/usersystemrole/se_sag/1");
		kombitRole1.setItSystem(kombit);
		kombitRole1.setName("Se sag");
		kombitRole1.setSupportedConstraintTypes(new ArrayList<>());
		kombitRole1.getSupportedConstraintTypes().add(constraintSupport);
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

	private OrgUnitAM createOU(String name) {
		OrgUnitAM ou = new OrgUnitAM();
		ou.setName(name);
		ou.setUuid(UUID.randomUUID().toString());
		ou.setChildren(new ArrayList<>());
		ou.setEmployees(new ArrayList<>());
		ou.setKleInterest(new ArrayList<>());
		ou.setKlePerforming(new ArrayList<>());

		return ou;
	}

	private UserAM createUser(String name, String title, String userId) {
		UserAM user = new UserAM();
		user.setName(name);
		user.setTitle(title);
		user.setUser_id(userId);
		user.setUuid(UUID.randomUUID().toString());

		return user;
	}
}
