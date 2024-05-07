package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.kombit.KOMBITService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class KOMBITTasks {
	private boolean initialized = false;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private KOMBITService kombitService;

	@PostConstruct
	public void init() {
		if (configuration.getScheduled().isEnabled() &&
			configuration.getIntegrations().getKombit().isEnabled()) {

			initialized = true;

			if (itSystemService.getBySystemType(ItSystemType.KOMBIT).size() <= 1) {
				kombitService.readAndUpdateItSystems();
			}
		}
	}

	@Scheduled(cron = "${cron.kombit.itsystems:0 #{new java.util.Random().nextInt(55)} 6,12,18 * * ?}")
	public void importItSystems() {
		if (initialized) {
			kombitService.readAndUpdateItSystems();
		}
	}
	
	@Scheduled(cron = "#{new java.util.Random().nextInt(59)} 0/2 6-21 * * ?")
	public void processUserRolesFromUpdateQueue() {
		if (initialized) {
			kombitService.synchronizeUserRoles();
		}
	}

	@Scheduled(cron = "#{new java.util.Random().nextInt(59)} 0/15 * * * *")
	public void synchronizeTest() {
		if (!configuration.getScheduled().isEnabled() || !configuration.getIntegrations().getKombit().isTestEnabled()) {
			return;
		}

		kombitService.synchronizeTest();
	}
}
