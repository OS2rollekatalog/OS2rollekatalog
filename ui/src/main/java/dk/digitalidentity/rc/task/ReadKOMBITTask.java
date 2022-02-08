package dk.digitalidentity.rc.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.SettingsDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITBrugersystemrolleDataafgraensningerDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITConstraintDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITConstraintsDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITDataafgraensningsVaerdier;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITItSystemDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITSystemRoleDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITUserRoleDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITVaerdiListeDTO;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
public class ReadKOMBITTask {
	private boolean initialized = false;

	@Autowired
	@Qualifier("kombitRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private SettingsDao settingsDao;

	@Autowired
	private UserRoleDao userRoleDao;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private ReadKOMBITTask self;

	@PostConstruct
	public void init() {
		if (configuration.getScheduled().isEnabled() &&
			configuration.getIntegrations().getKombit().isEnabled()) {
			
			initialized = true;
		}
		
		if (initialized && itSystemService.getBySystemType(ItSystemType.KOMBIT).size() == 0) {
			self.performRead();
		}
	}

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 6,12,18 * * ?")
	public void importItSystems() {
		self.performRead();
	}

	@Transactional
    public void performRead() {
		if (!initialized) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
		log.info("Starting to read it-system metadata from KOMBIT");

		try {
			KOMBITItSystemDTO[] itSystems = getItSystems();

			for (KOMBITItSystemDTO itSystemDTO : itSystems) {
				ItSystem itSystem = createOrUpdateItSystem(itSystemDTO);

				// if there has been no changes to the it-system, we get null back, which in certain special cases
				// can be an issue (e.g. the it-system uses a shared constrainttype, the constraintype has been updated
				// but the it-system has not)... these cases can be dealt with manually should they ever happen
				if (itSystem != null) {
					
					// read all roles on this it-system
					KOMBITSystemRoleDTO[] systemRolesDTO = getSystemRolesFromKOMBIT(itSystemDTO.getUuid());

					log.info("Changes detected on: " + itSystem.getName() + " with " + systemRolesDTO.length + " systemroles");

					// make sure constraints are available in the database
					updateConstraintTypes(systemRolesDTO);
	
					// then create (or update) the system roles on the it-system
					createOrUpdateSystemRoles(itSystem, systemRolesDTO);

					// finally delete any roles that are no longer present
					deleteSystemRoles(itSystem, systemRolesDTO);
				}
			}
	
			readExistingJobfunctionRoles();
			
			deleteMissingItSystems(itSystems);
			
			log.info("Done reading it-system metadata from KOMBIT");
		}
		catch (Exception ex) {
			log.error("Synchronizing it-systems from KOMBIT failed", ex);
		}
	}

	private void deleteMissingItSystems(KOMBITItSystemDTO[] kombitItSystems) {
		for (ItSystem itSystem : itSystemService.getBySystemTypeIncludingDeleted(ItSystemType.KOMBIT)) {
			boolean found = false;
			
			// do not delete the test-system
			if ("KOMBITTEST".equals(itSystem.getIdentifier())) {
				if (itSystem.isDeleted()) {
					itSystem.setDeleted(false);
					itSystemService.save(itSystem);
					
					log.info("Undeleting KOMBITTEST it-system");
				}

				continue;
			}
			
			if (itSystem.isDeleted()) {
				continue;
			}
			
			for (KOMBITItSystemDTO kombitItSystem : kombitItSystems) {
				if (kombitItSystem.getUuid().equals(itSystem.getUuid())) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				log.info("Deleting it-system " + itSystem.getName() + " because it does not exist in KOMBIT any more");
				itSystem.setDeleted(true);
				itSystem.setDeletedTimestamp(new Date());
				
				itSystemService.save(itSystem);
			}
		}
	}

	private void readExistingJobfunctionRoles() {
		Setting setting = settingsDao.getByKey("kombit_initial_sync_executed");
		if (setting == null) {
			setting = new Setting();
			setting.setKey("kombit_initial_sync_executed");
			setting.setValue("false");
		}
		
		boolean initialSyncExecuted = setting.getValue().equalsIgnoreCase("true");

		List<UserRole> existingDelegatedRoles = userRoleDao.getByDelegatedFromCvrNotNull();
		List<KOMBITUserRoleDTO> userRolesDTO = getUserRolesFromKOMBIT(); // TODO: supply initialSyncExecuted as a parameter, and then only read delegated roles if true (optimize external calls)

		for (KOMBITUserRoleDTO userRoleDTO : userRolesDTO) {
			ItSystem itSystem = null;
			boolean failed = false;
			boolean delegated = false;

			if (!userRoleDTO.getOrganisationCvr().equals(configuration.getCustomer().getCvr())) {
				delegated = true;
			}
			else if (initialSyncExecuted) {
				// we only do reverse sync of non-delegated Jobfunktionsroller on the very first run
				continue;
			}

			for (KOMBITBrugersystemrolleDataafgraensningerDTO systemRoleAssignmentDTO : userRoleDTO.getBrugersystemrolleDataafgraensninger()) {
				for (KOMBITDataafgraensningsVaerdier dataafgraensningsVaerdier : systemRoleAssignmentDTO.getDataafgraensningsVaerdier()) {
					if (dataafgraensningsVaerdier.getDynamisk() == true) {
						log.error("Cannot synchronize UserRole " + userRoleDTO.getEntityId() + " because it contains dynamic constraints, using keys unknown to the role catalogue.");
						failed = true;
					}
				}

				String uuid = systemRoleAssignmentDTO.getBrugersystemrolleUuid();
				SystemRole systemRole = systemRoleService.getByUuid(uuid);

				if (systemRole != null) {
					if (itSystem == null) {
						itSystem = systemRole.getItSystem();
					}
					else if (itSystem.getId() != systemRole.getItSystem().getId()) {
						log.error("Cannot synchronize UserRole " + userRoleDTO.getEntityId() + " because it covers more than one it-system.");
						failed = true;
					}
				}
			}
			
			if (failed) {
				continue;
			}

			if (itSystem == null) {
				log.warn("Failed to synchronize UserRole " + userRoleDTO.getEntityId() + " because no it-system was found in database that matches");
				continue;
			}

			String identifier = null;
			if (delegated) {
				identifier = userRoleDTO.getEntityId();

				// remove from list of existing delegated roles (we are using the residue for cleanup)
				for (Iterator<UserRole> iterator = existingDelegatedRoles.iterator(); iterator.hasNext();) {
					UserRole existingDelegatedRole = iterator.next();
					
					if (existingDelegatedRole.getIdentifier().equals(identifier)) {
						iterator.remove();
					}
				}
			}
			else {
				identifier = extractIdentifier(userRoleDTO.getEntityId());
			}

			if (identifier == null) {
				log.error("Cannot synchronize UserRole " + userRoleDTO.getEntityId() + " because it has an unparsable entityId.");
				continue;
			}
			
			if (delegated) {
				userRoleDTO.setBeskrivelse("(CVR: " + userRoleDTO.getOrganisationCvr() + ") " + userRoleDTO.getBeskrivelse());
			}

			// check if we already have it in our database
			UserRole userRole = userRoleDao.getByItSystemAndIdentifier(itSystem, identifier);
			if (userRole == null) {
				userRole = new UserRole();
				userRole.setSystemRoleAssignments(new ArrayList<>());
				userRole.setItSystem(itSystem);
				userRole.setIdentifier(identifier);				
				userRole.setDescription(userRoleDTO.getBeskrivelse());
				userRole.setName(userRoleDTO.getNavn());
			}
			userRole.setUuid(userRoleDTO.getUuid());

			boolean badRole = false;
			if (delegated) {
				userRole.setDelegatedFromCvr(userRoleDTO.getOrganisationCvr());
			}
			else {
				// we don't want to keep track of this information on delegated roles, as it is not given that
				// they follow our business rules, and we cannot edit them anyway.

				for (KOMBITBrugersystemrolleDataafgraensningerDTO brugersystemrolleDataafgraensninger : userRoleDTO.getBrugersystemrolleDataafgraensninger()) {
					SystemRole systemRole = systemRoleService.getByUuid(brugersystemrolleDataafgraensninger.getBrugersystemrolleUuid());
					if (systemRole == null) {
						log.warn("userRole = " + userRole.getName() + " : Could not find systemRole with entityId: " + brugersystemrolleDataafgraensninger.getBrugersystemrolleUuid());

						badRole = true;
						break;
					}
	
					SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
					systemRoleAssignment.setSystemRole(systemRole);
					systemRoleAssignment.setUserRole(userRole);
					systemRoleAssignment.setAssignedByName(SecurityUtil.getUserFullname());
					systemRoleAssignment.setAssignedByUserId(SecurityUtil.getUserId());
					systemRoleAssignment.setAssignedTimestamp(new Date());
					systemRoleAssignment.setConstraintValues(new ArrayList<>());
	
					for (KOMBITDataafgraensningsVaerdier dataafgraensningsVaerdier : brugersystemrolleDataafgraensninger.getDataafgraensningsVaerdier()) {
						SystemRoleAssignmentConstraintValue constraintValue = new SystemRoleAssignmentConstraintValue();
						constraintValue.setSystemRoleAssignment(systemRoleAssignment);

						ConstraintType constraintType = constraintTypeService.getByEntityId(dataafgraensningsVaerdier.getDataafgraensningstypeEntityId());
						if (constraintType == null) {
							log.warn("userRole = " + userRole.getName() + ", SystemRole = " + systemRole.getName() + " : Could not find contraintType with entityId: " + dataafgraensningsVaerdier.getDataafgraensningstypeEntityId());

							badRole = true;
							break;
						}
						constraintValue.setConstraintType(constraintType);
						constraintValue.setConstraintValue(dataafgraensningsVaerdier.getVaerdi());
						constraintValue.setConstraintValueType(ConstraintValueType.VALUE);
	
						systemRoleAssignment.getConstraintValues().add(constraintValue);
					}
					
					if (badRole) {
						break;
					}
	
					userRole.getSystemRoleAssignments().add(systemRoleAssignment);
				}
			}

			if (!badRole) {
				userRoleDao.save(userRole);
			}
		}
		
		// any existing delegated roles that where not part of the output, has to be deleted,
		// as it is no longer deleted, and needs to be removed from the UI
		userRoleDao.deleteAll(existingDelegatedRoles);

		setting.setValue("true");
		settingsDao.save(setting);
	}

	private String extractIdentifier(String entityId) {
		int endIdx = entityId.lastIndexOf("/");
		if (endIdx <= 0) {
			return null;
		}
		
		int startIdx = entityId.lastIndexOf("/", endIdx - 1);
		if (startIdx < 0) {
			return null;
		}

		return entityId.substring(startIdx + 1, endIdx);
	}

	private ItSystem createOrUpdateItSystem(KOMBITItSystemDTO itSystemDTO) {
		ItSystem itSystem = itSystemService.getByUuidIncludingDeleted(itSystemDTO.getUuid());
		if (itSystem != null && itSystemDTO.getChangedDate() == null) {
			return null;
		}

		else if (itSystem != null && (itSystem.getLastUpdated() == null || itSystem.getLastUpdated().before(itSystemDTO.getChangedDate()))) {
			itSystem.setName(itSystemDTO.getNavn());
			itSystem.setLastUpdated(itSystemDTO.getChangedDate());
			itSystem.setDeleted(false);
		}
		else if (itSystem == null) {
			itSystem = new ItSystem();
			itSystem.setUuid(itSystemDTO.getUuid());
			itSystem.setName(itSystemDTO.getNavn());
			itSystem.setIdentifier("KOMBIT");
			itSystem.setSystemType(ItSystemType.KOMBIT);
			itSystem.setLastUpdated(itSystemDTO.getChangedDate());
		}
		else {
			return null;
		}

		return itSystemService.save(itSystem);
	}

	private void createOrUpdateSystemRoles(ItSystem itSystem, KOMBITSystemRoleDTO[] systemRolesDTO) {
		for (KOMBITSystemRoleDTO systemRoleDTO : systemRolesDTO) {
			SystemRole systemRole = systemRoleService.getByUuid(systemRoleDTO.getUuid());
			if (systemRole == null) {
				systemRole = new SystemRole();
				systemRole.setItSystem(itSystem);
				systemRole.setRoleType(RoleType.BOTH);
				systemRole.setUuid(systemRoleDTO.getUuid());
				systemRole.setIdentifier(systemRoleDTO.getEntityId());
				systemRole.setSupportedConstraintTypes(new ArrayList<ConstraintTypeSupport>());
			}
			else {
				if (systemRole.getItSystem().getId() != itSystem.getId()) {
					log.error("Tried to update a systemRole from itSystem " + systemRole.getItSystem().getId() + " via itSystem " + itSystem.getId());
					continue;
				}
				
				// ok, make sure we actually only try to update if there are changes
				boolean changesToDescription = !Objects.equals(systemRole.getDescription(), systemRoleDTO.getBeskrivelse());
				boolean changesToName = !Objects.equals(systemRole.getName(), systemRoleDTO.getNavn());
				boolean changesToConstraints = areThereChangesToConstraints(systemRole, systemRoleDTO);
				
				if (!changesToDescription && !changesToName && !changesToConstraints) {
					log.info("No changes to: " + systemRoleDTO.getNavn());
					continue;
				}
			}
			
			log.info("Updating or creating: " + systemRoleDTO.getNavn());
			systemRole.setDescription(systemRoleDTO.getBeskrivelse());
			systemRole.setName(systemRoleDTO.getNavn());
			assignConstraintTypes(systemRoleDTO, systemRole);
			systemRoleService.save(systemRole);
		}
	}

	private void deleteSystemRoles(ItSystem itSystem, KOMBITSystemRoleDTO[] systemRolesDTO) {
		List<KOMBITSystemRoleDTO> systemRoleList = Arrays.asList(systemRolesDTO);
		List<SystemRole> existingSystemRoles = systemRoleService.findByItSystemAndUuidNotNull(itSystem);

		for (SystemRole systemRole : existingSystemRoles) {
			if (!systemRoleList.stream().anyMatch(sr -> sr.getUuid().equals(systemRole.getUuid()))) {
				systemRoleService.delete(systemRole);
			}
		}
	}

	private void updateConstraintTypes(KOMBITSystemRoleDTO[] systemRoles) {
		for (KOMBITSystemRoleDTO systemRoleDTO : systemRoles) {
			
			if (systemRoleDTO.getDataafgraensningstyper() != null) {

				for (KOMBITConstraintsDTO constraintDTO : systemRoleDTO.getDataafgraensningstyper()) {
					String uuid = constraintDTO.getDataafgraensningstype().getUuid();

					boolean changes = false;
					ConstraintType constraintType = constraintTypeService.getByUuid(uuid);
					if (constraintType == null) {
						constraintType = new ConstraintType();
						constraintType.setEntityId(constraintDTO.getDataafgraensningstype().getEntityId());
						constraintType.setUuid(constraintDTO.getDataafgraensningstype().getUuid());
						
						changes = true;
					}

					ConstraintUIType constraintUIType = null;
					switch (constraintDTO.getDataafgraensningstype().getType()) {
						case "SINGLE":
							constraintUIType = ConstraintUIType.COMBO_SINGLE;
							break;
						case "MULTI":
							constraintUIType = ConstraintUIType.COMBO_MULTI;
							break;
						case "TEXT":
							constraintUIType = ConstraintUIType.REGEX;
							break;
					}
					
					if (!Objects.equals(constraintUIType, constraintType.getUiType())) {
						constraintType.setUiType(constraintUIType);
						changes = true;
					}

					if (!Objects.equals(constraintDTO.getDataafgraensningstype().getNavn(), constraintType.getName())) {
						constraintType.setName(constraintDTO.getDataafgraensningstype().getNavn());
						changes = true;
					}

					if (!Objects.equals(constraintDTO.getDataafgraensningstype().getRegulaertUdtryk(), constraintType.getRegex())) {
						constraintType.setRegex(constraintDTO.getDataafgraensningstype().getRegulaertUdtryk());
						changes = true;
					}
					
					if (!Objects.equals(constraintDTO.getDataafgraensningstype().getBeskrivelse(), constraintType.getDescription())) {
						constraintType.setDescription(constraintDTO.getDataafgraensningstype().getBeskrivelse());
						changes = true;
					}

					if (constraintType.getValueSet() == null) {
						constraintType.setValueSet(new ArrayList<ConstraintTypeValueSet>());
					}

					for (KOMBITVaerdiListeDTO pair : constraintDTO.getDataafgraensningstype().getVaerdiListe()) {
						boolean found = false;
						
						for (ConstraintTypeValueSet constraintTypeValueSet : constraintType.getValueSet()) {
							if (Objects.equals(pair.getVaerdi(), constraintTypeValueSet.getConstraintKey())) {
								if (!Objects.equals(pair.getNavn(), constraintTypeValueSet.getConstraintValue())) {
									constraintTypeValueSet.setConstraintValue(pair.getNavn());
									changes = true;
								}

								found = true;
								break;
							}
						}
						
						if (!found) {
							ConstraintTypeValueSet entry = new ConstraintTypeValueSet();
							entry.setConstraintKey(pair.getVaerdi());
							entry.setConstraintValue(pair.getNavn());
							constraintType.getValueSet().add(entry);
							
							changes = true;
						}
					}

					for (Iterator<ConstraintTypeValueSet> iterator = constraintType.getValueSet().iterator(); iterator.hasNext();) {
						ConstraintTypeValueSet constraintTypeValueSet = iterator.next();						
						boolean found = false;
						
						for (KOMBITVaerdiListeDTO pair : constraintDTO.getDataafgraensningstype().getVaerdiListe()) {
							if (Objects.equals(pair.getVaerdi(), constraintTypeValueSet.getConstraintKey())) {
								found = true;
								break;
							}
						}
						
						if (!found) {
							iterator.remove();
							changes = true;
						}
					}

					if (changes) {
						constraintTypeService.save(constraintType);
					}
				}
			}
		}
	}

	private boolean areThereChangesToConstraints(SystemRole systemRole, KOMBITSystemRoleDTO systemRoleDTO) {

		if (getSizeOfCollection(systemRoleDTO.getDataafgraensningstyper()) > 0 || getSizeOfCollection(systemRole.getSupportedConstraintTypes()) > 0) {
			if (getSizeOfCollection(systemRoleDTO.getDataafgraensningstyper()) != getSizeOfCollection(systemRole.getSupportedConstraintTypes())) {
				// yep, something is different
				return true;
			}
			
			// ok, let's look at it then, they might still be identical
			for (KOMBITConstraintsDTO constraintsDTO : systemRoleDTO.getDataafgraensningstyper()) {
				KOMBITConstraintDTO constraintDTO = constraintsDTO.getDataafgraensningstype();

				// these are the values we compare against
				boolean mandatory = mapJaNejToBoolean(constraintsDTO.getKraevet());
				String uuid = constraintDTO.getUuid();
				
				boolean found = false;
				for (ConstraintTypeSupport constraintTypeSupport : systemRole.getSupportedConstraintTypes()) {
					// we only need to compare UUID's, as the actual constraint type is updated elsewhere,
					// though admittedly, if the constrainttype was updated (and only that), we would not
					// trigger an email event... hmmmm...
					if (constraintTypeSupport.getConstraintType().getUuid().equals(uuid)) {
						found = true;
						
						// if there are changes to mandatory'ness, then we have a change
						if (constraintTypeSupport.isMandatory() != mandatory) {
							return true;
						}
					}
				}
				
				// if we could not find it, we have a change
				if (!found) {
					return true;
				}
			}
			
			// no changes to constraints - same size, same types (same uuid and same mandatory'ness)
			return false;
		}

		// both had size 0, so there are no changes
		return false;
	}
	
	private long getSizeOfCollection(Collection<?> collection) {
		if (collection == null) {
			return 0;
		}
		
		return collection.size();
	}

	private void assignConstraintTypes(KOMBITSystemRoleDTO systemRoleDTO, SystemRole systemRole) {
		List<ConstraintTypeSupport> supportedConstraintTypes = systemRole.getSupportedConstraintTypes();
		List<KOMBITConstraintsDTO> supportedConstraintTypesDTO = systemRoleDTO.getDataafgraensningstyper();

		for (KOMBITConstraintsDTO supportedConstraintTypeDTO : supportedConstraintTypesDTO) {
			boolean found = false;
			
			for (ConstraintTypeSupport supportedConstraintType : supportedConstraintTypes) {
				if (supportedConstraintType.getConstraintType().getUuid().equals(supportedConstraintTypeDTO.getDataafgraensningstype().getUuid())) {
					found = true;

					supportedConstraintType.setMandatory(mapJaNejToBoolean(supportedConstraintTypeDTO.getKraevet()));
					break;
				}
			}

			if (!found) {
				ConstraintType ct = constraintTypeService.getByUuid(supportedConstraintTypeDTO.getDataafgraensningstype().getUuid());
				if (ct != null) {
					ConstraintTypeSupport newSupportedConstraintType = new ConstraintTypeSupport();
					newSupportedConstraintType.setMandatory(mapJaNejToBoolean(supportedConstraintTypeDTO.getKraevet()));
					newSupportedConstraintType.setConstraintType(ct);
					systemRole.getSupportedConstraintTypes().add(newSupportedConstraintType);
				}
				else {
					log.error("Role is using a contraint type that does not exist: " + systemRoleDTO.getNavn() + " / " + systemRoleDTO.getUuid() + " / " + supportedConstraintTypeDTO.getDataafgraensningstype().getUuid());
				}
			}
		}

		for (Iterator<ConstraintTypeSupport> iterator = supportedConstraintTypes.iterator(); iterator.hasNext();) {
			ConstraintTypeSupport supportedConstraintType = iterator.next();
			boolean found = false;

			for (KOMBITConstraintsDTO supportedConstraintTypeDTO : supportedConstraintTypesDTO) {
				if (supportedConstraintType.getConstraintType().getUuid().equals(supportedConstraintTypeDTO.getDataafgraensningstype().getUuid())) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				iterator.remove();
			}
		}
	}

	private boolean mapJaNejToBoolean(String kraevet) {
		if (kraevet.equals("JA")) {
			return true;
		}
		
		return false;
	}

	private List<KOMBITUserRoleDTO> getUserRolesFromKOMBIT() {
		int timeoutCount = 0;
		KOMBITUserRoleDTO[] userRoles = null;
	
		do {
			try {
				HttpEntity<KOMBITUserRoleDTO[]> userRolesEntity = restTemplate.getForEntity(configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller", KOMBITUserRoleDTO[].class);

				userRoles = userRolesEntity.getBody();
				break;
			}
			catch (ResourceAccessException ex) {
				timeoutCount++;

				if (timeoutCount >= 3) {
					throw ex;
				}

				log.warn("Timeout when calling: " + configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller, ex = " + ex.getMessage());
			}
		} while (true);

		List<KOMBITUserRoleDTO> result = new ArrayList<>();

		for (KOMBITUserRoleDTO userRoleKOMBITDTO : userRoles) {
			timeoutCount = 0;
			
			do {
				try {
					HttpEntity<KOMBITUserRoleDTO> userRoleEntity = restTemplate.getForEntity(configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/" + userRoleKOMBITDTO.getUuid(), KOMBITUserRoleDTO.class);
					result.add(userRoleEntity.getBody());
					break;
				}
				catch (ResourceAccessException ex) {
					timeoutCount++;
					
					if (timeoutCount >= 3) {
						throw ex;
					}

					log.warn("Timeout when calling: " + configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/" + userRoleKOMBITDTO.getUuid() + ", ex = " + ex.getMessage());
				}
			} while (true);
		}
		
		return result;
	}

	private KOMBITItSystemDTO[] getItSystems() throws Exception {
		int timeoutCount = 0;
		
		KOMBITItSystemDTO[] result = null;
		do {
			try {
				HttpEntity<KOMBITItSystemDTO[]> itSystemEntity = restTemplate.getForEntity(configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/brugervendtesystemer", KOMBITItSystemDTO[].class);

				result = itSystemEntity.getBody();
				break;
			}
			catch (ResourceAccessException ex) {
				timeoutCount++;
				
				if (timeoutCount >= 3) {
					throw ex;
				}

				log.warn("Timeout when calling: " + configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/brugervendtesystemer, ex = " + ex.getMessage());
			}
		} while (true);
		
		return result;
	}

	private KOMBITSystemRoleDTO[] getSystemRolesFromKOMBIT(String uuid) throws Exception {
		String url = configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/brugervendtesystemer/" + uuid + "/roller";
		int timeoutCount = 0;

		KOMBITSystemRoleDTO[] result = null;
		do {
			try {
				HttpEntity<KOMBITSystemRoleDTO[]> systemRoles = restTemplate.getForEntity(url, KOMBITSystemRoleDTO[].class);
				if (systemRoles.getBody() == null) {
					return new KOMBITSystemRoleDTO[0];
				}
				
				result = systemRoles.getBody();
				break;
			}
			catch (ResourceAccessException ex) {
				timeoutCount++;
				
				if (timeoutCount >= 3) {
					throw ex;
				}

				log.warn("Timeout when calling: " + url + ", ex = " + ex.getMessage());
			}
		} while (true);
		
		return result;
	}
}
