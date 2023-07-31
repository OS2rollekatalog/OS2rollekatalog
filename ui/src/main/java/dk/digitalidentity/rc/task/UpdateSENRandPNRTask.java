package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.SENumberService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class UpdateSENRandPNRTask {

	@Autowired
	private SENumberService sENumberService;
	
	@Autowired
	private PNumberService pNumberService;
	
	@Autowired
	private RoleCatalogueConfiguration config;
	
	// Run daily at 03:00 - 04:00
	@Scheduled(cron = "${cron.itSystemClean:0 #{new java.util.Random().nextInt(59)} 3 * * ?}")
	public void updateSENRDb() {
		if (!config.getScheduled().isEnabled() || !config.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
		sENumberService.updateSENR();
	}

	// Run daily at 03:00 - 04:00
	@Scheduled(cron = "${cron.itSystemClean:0 #{new java.util.Random().nextInt(59)} 3 * * ?}")
	public void updatePNRDb() {
		if (!config.getScheduled().isEnabled() || !config.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
		pNumberService.updatePNR();
	}

	// TODO: remove this once everyone is past NemLog-in implementation (assume 1/11-2023)
	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!config.getScheduled().isEnabled() || !config.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;			
		}
		
		if (pNumberService.getAll().size() == 0) {
			pNumberService.updatePNR();
			sENumberService.updateSENR();
		}
	}
}
