package dk.digitalidentity.rc.service.kombit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingKOMBITUpdate;
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
import dk.digitalidentity.rc.service.PendingKOMBITUpdateService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITBrugersystemrolleDataafgraensningerDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITConstraintDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITConstraintsDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITDataafgraensningsVaerdier;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITItSystemDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITSystemRoleDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITUserRoleDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITVaerdiListeDTO;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KOMBITService {
	private HttpHeaders headers; // TODO: do we need this?

	@Autowired
	@Qualifier("kombitRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	@Qualifier("kombitTestRestTemplate")
	private RestTemplate testRestTemplate;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private PendingKOMBITUpdateService pendingKOMBITUpdateService;

	@Autowired
	private UserRoleService userRoleService;
	
	// need this to bypass all the auditlogging for systemtasks (TODO: make the auditlogger accept systemtasks as bypassing auditlogging)
	@Autowired
	private UserRoleDao userRoleDao;
	
	@Autowired
	private SettingsService settingsService;

	// TODO: needed?
	public KOMBITService() {
		headers = new HttpHeaders();
		headers.add("content-type", "application/json");
	}

	@Transactional
    public void readAndUpdateItSystems() {
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
	
	@Transactional
	public void synchronizeUserRoles() {
		try {
			List<PendingKOMBITUpdate> pendingUserRoleUpdates = pendingKOMBITUpdateService.findAllByFailedFalse();
			if (pendingUserRoleUpdates == null || pendingUserRoleUpdates.size() == 0) {
				return;
			}
			
			log.info("Synchronizing " + pendingUserRoleUpdates.size() + " userroles to KOMBIT");
	
			for (PendingKOMBITUpdate pendingKOMBITUpdate : pendingUserRoleUpdates) {			
				boolean success = false;
	
				switch (pendingKOMBITUpdate.getEventType()) {
					case DELETE:
						success = deleteUserRole(pendingKOMBITUpdate);
						break;
					case UPDATE:
						if (pendingKOMBITUpdate.getUserRoleUuid() == null) {
							success = createUserRole(pendingKOMBITUpdate);
						}
						else {
							success = updateUserRole(pendingKOMBITUpdate);
						}
	
						break;
				}
	
				if (success) {
					pendingKOMBITUpdateService.delete(pendingKOMBITUpdate);
				}
			}
			
			log.info("Done synchronizing userroles to KOMBIT");
		}
		catch (Exception ex) {
			log.error("Synchronizing userRoles to KOMBIT failed", ex);
		}
	}

	@Transactional
    public void synchronizeTest() {
		try {
			ItSystem itSystem = itSystemService.getFirstByIdentifier("KOMBITTEST");
			if (itSystem == null) {
				log.error("Could not find it-system KOMBITTEST");
				return;
			}

			List<UserRole> existingDelegatedRoles = userRoleService.getByItSystemAndDelegatedFromCvrNotNull(itSystem);
			List<UserRole> existingUserRoles = userRoleService.getByItSystem(itSystem);
			List<String> userRoleIdentifiersFromKombit = new ArrayList<>();			

			for (KOMBITUserRoleDTO userRoleDTO : getUserRolesFromKOMBITTest()) {
				boolean delegated = false;

				if (!userRoleDTO.getOrganisationCvr().equals(configuration.getCustomer().getCvr())) {
					delegated = true;
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
				
				// check if we already have it in our database
				final String fIdentifier = identifier;
				UserRole userRole = existingUserRoles.stream().filter(u -> Objects.equals(u.getIdentifier(), fIdentifier)).findFirst().orElse(null);
				if (userRole == null) {
					userRole = new UserRole();
					userRole.setSystemRoleAssignments(new ArrayList<>());
					userRole.setItSystem(itSystem);
					userRole.setIdentifier(identifier);				
				}

				userRole.setUuid(userRoleDTO.getUuid());
				userRole.setName(userRoleDTO.getNavn());

				if (delegated) {
					userRole.setDelegatedFromCvr(userRoleDTO.getOrganisationCvr());
					userRole.setDescription("(CVR: " + userRoleDTO.getOrganisationCvr() + ") " + userRoleDTO.getBeskrivelse());
				}
				else {
					userRole.setDescription(userRoleDTO.getBeskrivelse());
				}

				userRoleIdentifiersFromKombit.add(identifier);
				userRoleDao.save(userRole);
			}
			
			// any existing delegated roles that where not part of the output, has to be deleted,
			// as it is no longer deleted, and needs to be removed from the UI
			userRoleDao.deleteAll(existingDelegatedRoles);
			
			// remove existing roles that were not in the KOMBIT read
			List<UserRole> toBeDeleted = existingUserRoles
					.stream()
					.filter(u -> !userRoleIdentifiersFromKombit.contains(u.getIdentifier()))
					.collect(Collectors.toList());

			userRoleService.deleteAll(toBeDeleted);
			
			log.info("Done reading user roles from KOMBIT Test");
		}
		catch (Exception ex) {
			log.error("Synchronizing user roles from KOMBIT Test failed", ex);
		}
	}
	
	private List<KOMBITUserRoleDTO> getUserRolesFromKOMBITTest() {
		int timeoutCount = 0;
		KOMBITUserRoleDTO[] userRoles = null;
	
		do {
			try {
				HttpEntity<KOMBITUserRoleDTO[]> userRolesEntity = testRestTemplate.getForEntity(configuration.getIntegrations().getKombit().getTestUrl() + "/jobfunktionsroller", KOMBITUserRoleDTO[].class);

				userRoles = userRolesEntity.getBody();
				break;
			}
			catch (ResourceAccessException ex) {
				timeoutCount++;

				if (timeoutCount >= 3) {
					throw ex;
				}

				log.warn("Timeout when calling: " + configuration.getIntegrations().getKombit().getTestUrl() + "/jobfunktionsroller, ex = " + ex.getMessage());
			}
		} while (true);

		List<KOMBITUserRoleDTO> result = new ArrayList<>();

		for (KOMBITUserRoleDTO userRoleKOMBITDTO : userRoles) {
			timeoutCount = 0;
			
			do {
				try {
					HttpEntity<KOMBITUserRoleDTO> userRoleEntity = testRestTemplate.getForEntity(configuration.getIntegrations().getKombit().getTestUrl() + "/jobfunktionsroller/" + userRoleKOMBITDTO.getUuid(), KOMBITUserRoleDTO.class);
					result.add(userRoleEntity.getBody());
					break;
				}
				catch (ResourceAccessException ex) {
					timeoutCount++;
					
					if (timeoutCount >= 3) {
						throw ex;
					}

					log.warn("Timeout when calling: " + configuration.getIntegrations().getKombit().getTestUrl() + "/jobfunktionsroller/" + userRoleKOMBITDTO.getUuid() + ", ex = " + ex.getMessage());
				}
			} while (true);
		}
		
		return result;
	}

	private boolean createUserRole(PendingKOMBITUpdate pendingKOMBITUpdate) {		
		UserRole userRole = userRoleService.getById(pendingKOMBITUpdate.getUserRoleId());
		if (userRole == null) {
			log.warn("Attempted to synchronize UserRole with id " + pendingKOMBITUpdate.getUserRoleId() + " but it does not exist anymore");
			
			// we return true to get it removed from the queue
			return true;
		}
		
		if (userRole.getDelegatedFromCvr() != null) {
			log.warn("Attempting to synchronize UserRole with id " + userRole.getId() + " which is a delegated role");
			return true;
		}

		String entityId = IdentifierGenerator.buildKombitIdentifier(userRole.getIdentifier(), configuration.getIntegrations().getKombit().getDomain());
		List<KOMBITBrugersystemrolleDataafgraensningerDTO> brugersystemrolleDataafgraensninger = new ArrayList<>();

		KOMBITUserRoleDTO userRoleDTO = new KOMBITUserRoleDTO();
		userRoleDTO.setNavn(userRole.getName());
		userRoleDTO.setEntityId(entityId);
		userRoleDTO.setOrganisationCvr(configuration.getCustomer().getCvr());
		userRoleDTO.setBeskrivelse(userRole.getDescription());
		userRoleDTO.setBrugersystemrolleDataafgraensninger(brugersystemrolleDataafgraensninger);

		// map all assigned systemroles and constraints to KOMBIT data-structure
		mapRoleAssignments(userRole, brugersystemrolleDataafgraensninger);

		// perform create (POST)
		try {
			String url = configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller";

			int timeoutCount = 0;
			KOMBITUserRoleDTO userRoleResponseDTO = null;
			do {
				try {
					HttpEntity<KOMBITUserRoleDTO> request = new HttpEntity<KOMBITUserRoleDTO>(userRoleDTO, headers);
					HttpEntity<KOMBITUserRoleDTO> userRoleResponse = restTemplate.exchange(url, HttpMethod.POST, request, KOMBITUserRoleDTO.class);
		
					userRoleResponseDTO = userRoleResponse.getBody();
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

			userRole.setUuid(userRoleResponseDTO.getUuid());

			userRoleDao.save(userRole);
			
			return true;
		}
		catch (HttpStatusCodeException ex) {
			if (ex.getStatusCode().equals(HttpStatus.NOT_ACCEPTABLE)) {
				pendingKOMBITUpdate.setFailed(true);
				pendingKOMBITUpdateService.save(pendingKOMBITUpdate);

				log.warn("Failed to create UserRole: " + userRole.getId() + " / " + ex.getResponseBodyAsString());
			}
			else {
				log.error("Failed to create UserRole: " + userRole.getId() + " / " + ex.getResponseBodyAsString(), ex);
			}
		}
		
		return false;
	}

	private boolean updateUserRole(PendingKOMBITUpdate pendingKOMBITUpdate) {
		UserRole userRole = userRoleService.getById(pendingKOMBITUpdate.getUserRoleId());
		if (userRole == null) {
			log.warn("Attempted to synchronize UserRole with id " + pendingKOMBITUpdate.getUserRoleId() + " but it does not exist anymore");
			
			// we return true to get it removed from the queue
			return true;
		}

		if (userRole.getDelegatedFromCvr() != null) {
			log.warn("Attempting to synchronize UserRole with id " + userRole.getId() + " which is a delegated role");
			return true;
		}

		List<KOMBITBrugersystemrolleDataafgraensningerDTO> brugersystemrolleDataafgraensninger = new ArrayList<>();
		String url = configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/" + pendingKOMBITUpdate.getUserRoleUuid();

		// read existing UserRole from KOMBIT (fallback to create-case if it does not exist)
		int timeoutCount = 0;
		KOMBITUserRoleDTO userRoleDTO = null;
		do {
			try {
				ResponseEntity<KOMBITUserRoleDTO> userRoleResponse = restTemplate.exchange(url, HttpMethod.GET, null, KOMBITUserRoleDTO.class);
				if (userRoleResponse.getStatusCodeValue() > 299) {
					return createUserRole(pendingKOMBITUpdate);
				}
				
				userRoleDTO = userRoleResponse.getBody();
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

		userRoleDTO.setBeskrivelse(userRole.getDescription());
		userRoleDTO.setNavn(userRole.getName());
		userRoleDTO.setBrugersystemrolleDataafgraensninger(brugersystemrolleDataafgraensninger);

		mapRoleAssignments(userRole, brugersystemrolleDataafgraensninger);

		// write back
		try {
			HttpEntity<KOMBITUserRoleDTO> request = new HttpEntity<KOMBITUserRoleDTO>(userRoleDTO, headers);
			
			timeoutCount = 0;
			do {
				try {
					restTemplate.exchange(url, HttpMethod.PUT, request, KOMBITUserRoleDTO.class);
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

			return true;
		}
		catch (HttpStatusCodeException ex) {
			if (ex.getStatusCode().equals(HttpStatus.NOT_ACCEPTABLE)) {
				pendingKOMBITUpdate.setFailed(true);
				pendingKOMBITUpdateService.save(pendingKOMBITUpdate);

				log.warn("Failed to update UserRole: " + userRole.getId() + " / " + ex.getResponseBodyAsString());
			}
			else {
				log.error("Failed to update UserRole: " + userRole.getId() + " / " + ex.getResponseBodyAsString(), ex);
			}
		}
				
		return false;
	}
	
	private boolean deleteUserRole(PendingKOMBITUpdate pendingKOMBITUpdate) {
		if (pendingKOMBITUpdate.getUserRoleUuid() == null) {
			log.error("Cannot delete UserRole without UUID.");

			return true;
		}

		String url = configuration.getIntegrations().getKombit().getUrl() + "/jobfunktionsroller/" + pendingKOMBITUpdate.getUserRoleUuid();

		try {
			int timeoutCount = 0;
			do {
				try {
					restTemplate.delete(url);
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

			return true;
		}
		catch (HttpStatusCodeException ex) {
			log.error("Failed to delete UserRole: " + pendingKOMBITUpdate.getUserRoleUuid() + " / " + ex.getResponseBodyAsString(), ex);
		}

		return false;
	}
	
	private void mapRoleAssignments(UserRole userRole, List<KOMBITBrugersystemrolleDataafgraensningerDTO> brugersystemrolleDataafgraensninger) {
		for (SystemRoleAssignment systemRoleAssignment : userRole.getSystemRoleAssignments()) {
			KOMBITBrugersystemrolleDataafgraensningerDTO systemRoleAssignmentDTO = new KOMBITBrugersystemrolleDataafgraensningerDTO();
			systemRoleAssignmentDTO.setBrugersystemrolleUuid(systemRoleAssignment.getSystemRole().getUuid());

			List<KOMBITDataafgraensningsVaerdier> dataafgraensningsVaerdier = new ArrayList<>();
			for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
				KOMBITDataafgraensningsVaerdier dataConstraintValue = new KOMBITDataafgraensningsVaerdier();

				switch (constraintValue.getConstraintValueType()) {
					case EXTENDED_INHERITED:
					case INHERITED:
					case READ_AND_WRITE:
					case LEVEL_1:
					case LEVEL_2:
					case LEVEL_3:
					case LEVEL_4:
					case POSTPONED:
						dataConstraintValue.setDynamisk(true);
						dataConstraintValue.setVaerdi(constraintValue.getConstraintIdentifier());
						break;
					case VALUE:
						dataConstraintValue.setDynamisk(false);
						dataConstraintValue.setVaerdi(fixConstraintValue(userRole, constraintValue));
						break;
				}

				dataConstraintValue.setDataafgraensningstypeEntityId(constraintValue.getConstraintType().getEntityId());
				dataConstraintValue.setDataafgraensningstypeNavn(constraintValue.getConstraintType().getName());

				dataafgraensningsVaerdier.add(dataConstraintValue);
			}

			systemRoleAssignmentDTO.setDataafgraensningsVaerdier(dataafgraensningsVaerdier);
			brugersystemrolleDataafgraensninger.add(systemRoleAssignmentDTO);
		}
	}

	// We want to perform a bit of validation before we send the value,
	// as the vendor of the it-system might have changed the validation rules,
	// and the change that triggered a re-provision might not have affected
	// the constraint values. We cannot catch everything, but some cases we
	// might be able to cleanup combined with a bit of WARN logging
	private String fixConstraintValue(UserRole userRole, SystemRoleAssignmentConstraintValue constraintValue) {
		if (constraintValue.getConstraintValue() == null) {
			return null;
		}

		String value = constraintValue.getConstraintValue();
		switch (constraintValue.getConstraintType().getUiType()) {
			case COMBO_MULTI:
				String[] values = value.split(",");
				StringBuilder builder = new StringBuilder();
				
				for (String val : values) {
					boolean found = false;

					for (ConstraintTypeValueSet ctvs : constraintValue.getConstraintType().getValueSet()) {
						if (ctvs.getConstraintKey().equals(val)) {
							found = true;
							break;
						}
					}
					
					if (!found) {
						log.warn("Skipping constraint value '" + val + "' on userRole " + userRole.getId() + " because it is no longer a valid constraint value");
					}
					else {
						if (builder.length() > 0) {
							builder.append(",");
						}

						builder.append(val);
					}
				}
				
				value = builder.toString();
				break;
			case COMBO_SINGLE:
			case REGEX:
				// nothing we can do for these
				break;
		}
		
		return value;
	}

	private void readExistingJobfunctionRoles() {
		Setting setting = settingsService.getByKey("kombit_initial_sync_executed");
		if (setting == null) {
			setting = new Setting();
			setting.setKey("kombit_initial_sync_executed");
			setting.setValue("false");
		}
		
		boolean initialSyncExecuted = setting.getValue().equalsIgnoreCase("true");

		List<UserRole> existingDelegatedRoles = userRoleService.getByDelegatedFromCvrNotNullAndItSystemIdentifierNot("KOMBITTEST");
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
			UserRole userRole = userRoleService.getByItSystemAndIdentifier(itSystem, identifier);
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
		settingsService.save(setting);
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
}
