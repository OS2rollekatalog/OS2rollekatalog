package dk.digitalidentity.rc.bootstrap;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.SystemRoleAssignmentConstraintValueDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PostponedConstraintService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.dao.model.enums.NotificationType.ORG_UNIT_NAME_CHANGED;

@Slf4j
@Order(100)
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataBootstrap implements ApplicationListener<ApplicationReadyEvent> {
	private final PlatformTransactionManager transactionManager;
	private final SettingsService settingsService;
	private final SystemRoleService systemRoleService;
	private final UserRoleService userRoleService;
	private final ConstraintTypeService constraintTypeService;
	private final ItSystemService itSystemService;
	private final SystemRoleAssignmentConstraintValueDao systemRoleAssignmentConstraintValueDao;
	private final PostponedConstraintService postponedConstraintService;
	private final UserService userService;

	@Override
	public void onApplicationEvent(final @NotNull ApplicationReadyEvent event) {
		Integer currentVersion = getCurrentVersion();
		currentVersion = applyVersion(1, this::seedV1, currentVersion);
		currentVersion = applyVersion(2, this::seedV2, currentVersion);
		currentVersion = applyVersion(3, this::seedV3, currentVersion);
		currentVersion = applyVersion(4, this::seedV4, currentVersion);
		setCurrentVersion(currentVersion);
	}

	private Integer getCurrentVersion() {
		return settingsService.getDataSeedVersion();
	}

	private void setCurrentVersion(final Integer currentVersion) {
		settingsService.setDataSeedVersion(currentVersion);
	}

	/**
	 * Applies the given function if the current version is lower than the version number for this
	 *
	 * @param version        The version number of this function
	 * @param applier        the function to run
	 * @param currentVersion the current version number
	 * @return the current version number, updated if this version was applied successfully
	 */
	private Integer applyVersion(final int version, final Runnable applier, Integer currentVersion) {
		final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		if (currentVersion < version) {
			try {
				transactionTemplate.executeWithoutResult(_ -> applier.run());
				return version;
			} catch (Exception e) {
				log.error("Failed to apply version {}", version, e);
				throw e;
			}
		}
		return currentVersion;
	}

	private void seedV1() {
		// find relevant supported constraint types
		ConstraintType itSystemConstraint =  constraintTypeService.getByEntityId(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID);
		ConstraintType ouConstraintType =  constraintTypeService.getByEntityId(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID);

		ConstraintTypeSupport systemConstraintTypeSupport = new ConstraintTypeSupport();
		systemConstraintTypeSupport.setConstraintType(itSystemConstraint);
		systemConstraintTypeSupport.setMandatory(false);

		ConstraintTypeSupport ouConstraintTypeSupport = new ConstraintTypeSupport();
		ouConstraintTypeSupport.setConstraintType(ouConstraintType);
		ouConstraintTypeSupport.setMandatory(false);

		// create the new system roles
		record SystemRoleTemplate(String title, String constantId, String description, List<ConstraintTypeSupport> supportedConstraintTypes) {}
		List<SystemRoleTemplate> userRoleRoleTemplates = List.of(
				new SystemRoleTemplate("Jobfunktionsrolle - Læs", Constants.ROLE_USERROLE_READ_ID, "Denne rolle giver adgang til at se Jobfunktionsroller", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport)),
				new SystemRoleTemplate("Jobfunktionsrolle - Opdater", Constants.ROLE_USERROLE_UPDATE_ID, "Denne rolle giver adgang til at se og ændre i Jobfunktionsroller", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport)),
				new SystemRoleTemplate("Jobfunktionsrolle - Opret", Constants.ROLE_USERROLE_CREATE_ID, "Denne rolle giver adgang til at se og oprette nye Jobfunktionsroller", List.of(systemConstraintTypeSupport)),
				new SystemRoleTemplate("Jobfunktionsrolle - Slet", Constants.ROLE_USERROLE_DELETE_ID, "Denne rolle giver adgang til at se og slette Jobfunktionsroller", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport))
		);
		List<SystemRoleTemplate> rolegroupRoleTemplates = List.of(
				new SystemRoleTemplate("Rollebuket - Læs", Constants.ROLE_ROLEGROUP_READ_ID, "Denne rolle giver adgang til at se Rollebuketter", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport)),
				new SystemRoleTemplate("Rollebuket - Opdater", Constants.ROLE_ROLEGROUP_UPDATE_ID, "Denne rolle giver adgang til at se og ændre i Rollebuketter", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport)),
				new SystemRoleTemplate("Rollebuket - Opret", Constants.ROLE_ROLEGROUP_CREATE_ID, "Denne rolle giver adgang til at se og oprette nye Rollebuketter", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport)),
				new SystemRoleTemplate("Rollebuket - Slet", Constants.ROLE_ROLEGROUP_DELETE_ID, "Denne rolle giver adgang til at se og slette Rollebuketter", List.of(systemConstraintTypeSupport, ouConstraintTypeSupport))
		);
		List<SystemRoleTemplate> itSystemTemplates = List.of(
				new SystemRoleTemplate("IT System - Læs", Constants.ROLE_ITSYSTEM_READ_ID, "Denne rolle giver adgang til at se IT systemer", List.of(systemConstraintTypeSupport)),
				new SystemRoleTemplate("IT System - Opdater", Constants.ROLE_ITSYSTEM_UPDATE_ID, "Denne rolle giver adgang til at se og ændre i IT systemer", List.of(systemConstraintTypeSupport)),
				new SystemRoleTemplate("IT System - Opret", Constants.ROLE_ITSYSTEM_CREATE_ID, "Denne rolle giver adgang til at se og oprette nye IT systemer", List.of()),
				new SystemRoleTemplate("IT System - Slet", Constants.ROLE_ITSYSTEM_DELETE_ID, "Denne rolle giver adgang til at se og slette IT systemer", List.of(systemConstraintTypeSupport))
		);
		List<SystemRoleTemplate> ouRoleTemplates = List.of(
				new SystemRoleTemplate("Enhed - Læs", Constants.ROLE_OU_READ_ID, "Denne rolle giver adgang til at se Enheder", List.of( ouConstraintTypeSupport)),
				new SystemRoleTemplate("Enhed - Opdater", Constants.ROLE_OU_UPDATE_ID, "Denne rolle giver adgang til at ændre i Enheder", List.of( ouConstraintTypeSupport))
		);
		List<SystemRoleTemplate> userRoleTemplates = List.of(
				new SystemRoleTemplate("Bruger - Læs", Constants.ROLE_USER_READ_ID, "Denne rolle giver adgang til at se Brugere", List.of( ouConstraintTypeSupport)),
				new SystemRoleTemplate("Bruger - Opdater", Constants.ROLE_USER_UPDATE_ID, "Denne rolle giver adgang til at ændre i Brugere", List.of( ouConstraintTypeSupport))
		);
		List<SystemRoleTemplate> reportRoleTemplates = List.of(
				new SystemRoleTemplate("Auditlog", Constants.ROLE_LOG_READ_ID, "Denne rolle giver adgang til at se auditloggen", List.of()),
				new SystemRoleTemplate("Adviser", Constants.ROLE_ADVISE_READ_ID, "Denne rolle giver adgang til at se og ændre i Adviser", List.of()),
				new SystemRoleTemplate("Ledere - Læs", Constants.ROLE_MANAGER_READ_ID, "Denne rolle giver adgang til at se ledere og relaterede indstillinger", List.of()),
				new SystemRoleTemplate("Ledere - Administrer", Constants.ROLE_MANAGER_UPDATE_ID, "Denne rolle giver adgang til at ændre i ledere og relaterede indstillinger", List.of())
		);
		List<SystemRoleTemplate> administrationRoleTemplates = List.of(
				new SystemRoleTemplate("Administration", Constants.ROLE_CONFIG_READ_ID, "Denne rolle giver adgang til at se og ændre i instillinger for systemet", List.of())
		);


		List<SystemRoleTemplate> roleTemplates = new ArrayList<>();
		roleTemplates.addAll(userRoleRoleTemplates);
		roleTemplates.addAll(rolegroupRoleTemplates);
		roleTemplates.addAll(itSystemTemplates);
		roleTemplates.addAll(ouRoleTemplates);
		roleTemplates.addAll(userRoleTemplates);
		roleTemplates.addAll(reportRoleTemplates);
		roleTemplates.addAll(administrationRoleTemplates);

		Map<String, SystemRole> systemRolesByIdentifier = new HashMap<>();
		ItSystem roleCatalogue = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		for (SystemRoleTemplate srt : roleTemplates) {
			SystemRole systemRole = systemRoleService.createForRoleCatalogue(srt.title, srt.constantId, srt.description, roleCatalogue);
			systemRole.getSupportedConstraintTypes().addAll(srt.supportedConstraintTypes);
			systemRolesByIdentifier.put(srt.constantId, systemRole);
		}

		// create new user roles
		String assignedBy = "Systembruger";
		String assignedById = "system";

		userRoleService.createForSystemRoles(
				"Jobfunktionsrolle Admin",
				"Denne rolle har rettigheder til at oprette, ændre og slette jobfunktionsroller",
				roleCatalogue,
				"Userrole Admin",
				List.of(
						systemRolesByIdentifier.get(Constants.ROLE_USERROLE_READ_ID),
						systemRolesByIdentifier.get(Constants.ROLE_USERROLE_UPDATE_ID),
						systemRolesByIdentifier.get(Constants.ROLE_USERROLE_CREATE_ID),
						systemRolesByIdentifier.get(Constants.ROLE_USERROLE_DELETE_ID)
				),
				assignedBy,
				assignedById
		);
		userRoleService.createForSystemRoles(
				"Jobfunktionsrolle redaktør",
				"Denne rolle har rettigheder til at ændre i jobfunktionsroller, men kan hverken oprette eller slette",
				roleCatalogue,
				"Userrole updater",
				List.of(
						systemRolesByIdentifier.get(Constants.ROLE_USERROLE_READ_ID),
						systemRolesByIdentifier.get(Constants.ROLE_USERROLE_UPDATE_ID)
				),
				assignedBy,
				assignedById
		);

		userRoleService.createForSystemRoles(
				"Rollebuket Admin",
				"Denne rolle har rettigheder til at oprette, ændre og slette rollebuketter",
				roleCatalogue,
				"Rolegroup Admin",
				List.of(
						systemRolesByIdentifier.get(Constants.ROLE_ROLEGROUP_READ_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ROLEGROUP_UPDATE_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ROLEGROUP_CREATE_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ROLEGROUP_DELETE_ID)
				),
				assignedBy,
				assignedById
		);
		userRoleService.createForSystemRoles(
				"Rollebuket redaktør",
				"Denne rolle har rettigheder til at ændre i rollebuketter, men kan hverken oprette eller slette",
				roleCatalogue,
				"Rolegroup updater",
				List.of(
						systemRolesByIdentifier.get(Constants.ROLE_ROLEGROUP_READ_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ROLEGROUP_UPDATE_ID)
				),
				assignedBy,
				assignedById
		);

		userRoleService.createForSystemRoles(
				"It system Admin",
				"Denne rolle har rettigheder til at oprette, ændre og slette IT systemer",
				roleCatalogue,
				"Itsystem Admin",
				List.of(
						systemRolesByIdentifier.get(Constants.ROLE_ITSYSTEM_READ_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ITSYSTEM_UPDATE_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ITSYSTEM_CREATE_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ITSYSTEM_DELETE_ID)
				),
				assignedBy,
				assignedById
		);
		userRoleService.createForSystemRoles(
				"It system redaktør",
				"Denne rolle har rettigheder til at ændre i IT systemer, men kan hverken oprette eller slette",
				roleCatalogue,
				"Itsystem updater",
				List.of(
						systemRolesByIdentifier.get(Constants.ROLE_ITSYSTEM_READ_ID),
						systemRolesByIdentifier.get(Constants.ROLE_ITSYSTEM_UPDATE_ID)
				),
				assignedBy,
				assignedById
		);
	}

	private void seedV2() {
		ItSystem roleCatalogue = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER).stream().findFirst()
				.orElseThrow();
		String oldAssignerRoleIdentifier = "http://rollekatalog.dk/assigner";
		String assignedBy = "Systembruger";
		String assignedById = "system";

		// change idenfitier on old assigner role to user assigner and rename
		SystemRole userAssigner = systemRoleService.getFirstByIdentifierAndItSystemId(oldAssignerRoleIdentifier, roleCatalogue.getId());
		userAssigner.setIdentifier(Constants.ROLE_USER_ASSIGNER_ID);

		// find supported constraint types
		ConstraintType itSystemConstraint =  constraintTypeService.getByEntityId(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID);
		ConstraintType ouConstraintType =  constraintTypeService.getByEntityId(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID);

		ConstraintTypeSupport systemConstraintTypeSupport = new ConstraintTypeSupport();
		systemConstraintTypeSupport.setConstraintType(itSystemConstraint);
		systemConstraintTypeSupport.setMandatory(false);

		ConstraintTypeSupport ouConstraintTypeSupport = new ConstraintTypeSupport();
		ouConstraintTypeSupport.setConstraintType(ouConstraintType);
		ouConstraintTypeSupport.setMandatory(false);

		// create new ou assigner role
		record SystemRoleTemplate(String title, String constantId, String description, List<ConstraintTypeSupport> supportedConstraintTypes) {}
		SystemRoleTemplate ouAssignerTemplate = new SystemRoleTemplate(
				"Rolletildeler - Enheder",
				Constants.ROLE_OU_ASSIGNER_ID,
				"Denne rolle giver adgant til at tildele og fjerne jobfunktionsroller og rollebuketter til enheder",
				List.of(systemConstraintTypeSupport, ouConstraintTypeSupport));

		SystemRole ouAssigner = systemRoleService.createForRoleCatalogue(ouAssignerTemplate.title, ouAssignerTemplate.constantId, ouAssignerTemplate.description, roleCatalogue);
		ouAssigner.getSupportedConstraintTypes().addAll(ouAssignerTemplate.supportedConstraintTypes);

		userAssigner.setName("Rolletildeler - Brugere");
		userAssigner.setDescription("Denne rolle giver adgant til at tildele og fjerne jobfunktionsroller og rollebuketter til brugere");
		systemRoleService.saveAll(List.of(ouAssigner, userAssigner));

		// find all assignments to old role
		Set<UserRole> userRolesWithOldAssigner = userRoleService.findAllBySystemRole(userAssigner);
		for(UserRole userRole : userRolesWithOldAssigner) {
			List<SystemRoleAssignment> assignments = userRole.getSystemRoleAssignments();
			Set<SystemRoleAssignment> userAssignerAssignment = assignments.stream()
					.filter(a -> a.getSystemRole().getIdentifier().equals(Constants.ROLE_USER_ASSIGNER_ID))
					.collect(Collectors.toSet());

			// create a new assignment for ouAssigner with same constraints
			if (!userAssignerAssignment.isEmpty()) {
				for (SystemRoleAssignment systemRoleAssignment : userAssignerAssignment) {

					SystemRoleAssignment ouAssignerAssignment = userRoleService.createSystemRoleAssignment(ouAssigner, userRole, assignedBy, assignedById);

					List<SystemRoleAssignmentConstraintValue> userAssignerConstraints = systemRoleAssignmentConstraintValueDao.findBySystemRoleAssignment(systemRoleAssignment);
					for (SystemRoleAssignmentConstraintValue userAssignerConstraintValue : userAssignerConstraints) {
						final SystemRoleAssignmentConstraintValue ouConstraint = new SystemRoleAssignmentConstraintValue();
						ouConstraint.setConstraintType(userAssignerConstraintValue.getConstraintType());
						ouConstraint.setSystemRoleAssignment(ouAssignerAssignment);
						ouConstraint.setConstraintValue(userAssignerConstraintValue.getConstraintValue());
						ouConstraint.setConstraintValueType(userAssignerConstraintValue.getConstraintValueType());
						ouConstraint.setPostponed(userAssignerConstraintValue.isPostponed());

						userRoleService.addSystemRoleConstraint(ouAssignerAssignment, ouConstraint);
					}

					// add assignment to user role
					assignments.add(systemRoleAssignment);
				}
			}

			// also duplicate any postponed constraints for the role
			Set<PostponedConstraint> postponedConstraints = postponedConstraintService.findAllForSystemRoleAndUserRole(oldAssignerRoleIdentifier, userRole);
			for (PostponedConstraint postponedConstraint : postponedConstraints) {
				PostponedConstraint ouPostponedConstraint = new PostponedConstraint();
				ouPostponedConstraint.setConstraintType(postponedConstraint.getConstraintType());
				ouPostponedConstraint.setValue(postponedConstraint.getValue());
				ouPostponedConstraint.setSystemRole(ouAssigner);
				ouPostponedConstraint.setUserUserRoleAssignment(postponedConstraint.getUserUserRoleAssignment());
				postponedConstraintService.save(ouPostponedConstraint);
			}

			userRoleService.save(userRole);
		}
	}

	private void seedV3() {
		userService.queueAllForRecalculation();
	}

	private void seedV4() {
		settingsService.setNotificationTypeEnabled(ORG_UNIT_NAME_CHANGED, false);
	}

}
