package dk.digitalidentity.rc.task;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StartupInitJob {

	@Autowired
	private ReadKleTask kleReader;
	
	@Autowired
	private KspCicsUpdateTask kspCicsUpdateTask;
	
	@PostConstruct
	public void init() {
		kleReader.init();
		kspCicsUpdateTask.init();
	}
}
