package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.service.orgunit.OrgUnitNearestManagerService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StartupInitJob {

	@Autowired
	private ReadKleTask kleReader;

	@Autowired
	private KspCicsUpdateTask kspCicsUpdateTask;

	@Autowired
	private OrgUnitNearestManagerService orgUnitNearestManagerService;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		kleReader.init();
		kspCicsUpdateTask.init();

		try {
			orgUnitNearestManagerService.recompute();
		}
		catch (Exception ex) {
			log.error("Failed to recompute nearest managers at startup", ex);
		}
	}
}
