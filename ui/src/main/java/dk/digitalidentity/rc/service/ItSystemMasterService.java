package dk.digitalidentity.rc.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.dao.ItSystemMasterDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemMaster;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.service.master.dto.ItSystemMasterDTO;
import dk.digitalidentity.rc.service.master.dto.SystemRoleMasterDTO;
import lombok.extern.log4j.Log4j;

@Log4j
@Service
public class ItSystemMasterService {

	@Value("${itsystem.master.url}")
	private String baseUrl;

	@Autowired
	@Qualifier("defaultRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private ItSystemMasterDao itSystemMasterDao;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	public List<ItSystemMaster> findAll() {
		return itSystemMasterDao.findAll();
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void updateLocalItSystems() {
		List<ItSystem> subscribed = itSystemService.getBySubscribedToNotNull();
		List<ItSystemMaster> masterList = findAll();

		for (ItSystem itSystem : subscribed) {
			for (ItSystemMaster master : masterList) {
				if (itSystem.getSubscribedTo().equals(master.getMasterId()) && (itSystem.getLastUpdated() == null || itSystem.getLastUpdated().before(master.getLastModified()))) {
					String url = baseUrl + "/api/itsystem/" + itSystem.getSubscribedTo();
					
					try {
						ResponseEntity<ItSystemMasterDTO> response = restTemplate.getForEntity(url, ItSystemMasterDTO.class);
						if (response.getStatusCode().is2xxSuccessful()) {
							List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
							List<SystemRoleMasterDTO> masterSystemRoles = response.getBody().getSystemRoles();

							// systemRoles to remove
							for (SystemRole systemRole : systemRoles) {
								boolean found = false;

								for (SystemRoleMasterDTO masterSystemRole : masterSystemRoles) {
									if (systemRole.getIdentifier().equals(masterSystemRole.getIdentifier())) {
										found = true;
									}
								}
								
								if (!found) {
									log.info("Removing systemrole '" + systemRole.getName() + "' from ItSystem '" + itSystem.getName() + "' because it no longer exists in master system");

									systemRoleService.delete(systemRole);
								}
							}

							// systemRoles to add
							for (SystemRoleMasterDTO masterSystemRole : masterSystemRoles) {
								boolean found = false;

								for (SystemRole systemRole : systemRoles) {
									if (systemRole.getIdentifier().equals(masterSystemRole.getIdentifier())) {
										found = true;
									}
								}
								
								if (!found) {
									log.info("Adding systemrole '" + masterSystemRole.getName() + "' to ItSystem '" + itSystem.getName() + "'");

									SystemRole systemRole = new SystemRole();
									systemRole.setName(masterSystemRole.getName());
									systemRole.setIdentifier(masterSystemRole.getIdentifier());
									systemRole.setItSystem(itSystem);
									systemRole.setDescription(masterSystemRole.getDescription());
									systemRole.setRoleType(RoleType.BOTH);
									
									systemRoleService.save(systemRole);									
								}
							}

							// systemRoles to modify
							for (SystemRoleMasterDTO masterSystemRole : masterSystemRoles) {
								for (SystemRole systemRole : systemRoles) {
									if (systemRole.getIdentifier().equals(masterSystemRole.getIdentifier())) {
										if ((systemRole.getDescription() == null && masterSystemRole.getDescription() != null) ||
											(systemRole.getName() == null && masterSystemRole.getName() != null) ||
											(systemRole.getDescription() != null && !systemRole.getDescription().equals(masterSystemRole.getDescription())) ||
											(systemRole.getName() != null && !systemRole.getName().equals(masterSystemRole.getName()))) {

											log.info("Modifying systemrole '" + masterSystemRole.getName() + "' for ItSystem '" + itSystem.getName() + "'");

											systemRole.setName(masterSystemRole.getName());
											systemRole.setDescription(masterSystemRole.getDescription());

											systemRoleService.save(systemRole);
										}
									}
								}								
							}

							itSystem.setLastUpdated(new Date());
							itSystemService.save(itSystem);
						}
						else {
							log.error("Failed to fetch updated systemroles (" + url + ") for it-system: " + itSystem.getId() + ", due to HTTP: " + response.getStatusCodeValue());
						}
					}
					catch (Exception ex) {
						log.error("Failed to fetch updated systemroles for it-system: " + itSystem.getId(), ex);
					}
				}
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void fetchItSystems() {
		String url = baseUrl + "/api/itsystems";
		try {
			ResponseEntity<ItSystemMasterDTO[]> response = restTemplate.getForEntity(url, ItSystemMasterDTO[].class);

			if (response.getStatusCode().is2xxSuccessful()) {
				for (ItSystemMasterDTO sys : response.getBody()) {
					ItSystemMaster itSystem = itSystemMasterDao.findByMasterId(sys.getMasterId());
	
					if (itSystem == null) {
						itSystem = new ItSystemMaster();
						itSystem.setMasterId(sys.getMasterId());
						itSystem.setName(sys.getName());
						itSystem.setLastModified(sys.getLastModified());
					}
					else {
						itSystem.setName(sys.getName());
						itSystem.setLastModified(sys.getLastModified());
					}
	
					itSystemMasterDao.save(itSystem);
				}
			}
			else {
				log.error("Failed to connect to master of it-systems (" + url + "), HTTP: " + response.getStatusCodeValue());
			}
		}
		catch (Exception ex) {
			log.error("Failed to connect to master of it-systems", ex);
		}
	}
}
