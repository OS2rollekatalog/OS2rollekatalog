package dk.digitalidentity.rc.task;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.PendingKOMBITUpdate;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.PendingKOMBITUpdateService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITBrugersystemrolleDataafgraensningerDTO;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITDataafgraensningsVaerdier;
import dk.digitalidentity.rc.service.kombit.dto.KOMBITUserRoleDTO;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
public class UpdateKOMBITTask {
	private HttpHeaders headers;
	private boolean initialized = false;

	@Autowired
	@Qualifier("kombitRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private PendingKOMBITUpdateService pendingKOMBITUpdateService;

	@Autowired
	private UpdateKOMBITTask self;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@PostConstruct
	public void init() {
		if (configuration.getScheduled().isEnabled() &&
			configuration.getIntegrations().getKombit().isEnabled()) {

			initialized = true;
			log.info("KOMBIT synchronization enabled on this instance!");
			
			headers = new HttpHeaders();
			headers.add("content-type", "application/json");
		}
		else {
			log.info("KOMBIT synchronization disabled on this instance!");
		}
	}

	@Scheduled(cron = "0 0/2 6-21 * * ?")
	public void processUserRolesFromUpdateQueue() {
		self.performUpdate();
	}
	
	@Transactional
	public void performUpdate() {
		if (!initialized) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

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

			userRoleService.save(userRole);
			
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
}
