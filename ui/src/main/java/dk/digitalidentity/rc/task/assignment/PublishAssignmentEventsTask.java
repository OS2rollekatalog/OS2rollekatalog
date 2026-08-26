package dk.digitalidentity.rc.task.assignment;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.assignment.PublishAssignmentService;
import lombok.AllArgsConstructor;

@Component
@EnableScheduling
@AllArgsConstructor
public class PublishAssignmentEventsTask {
	private final PublishAssignmentService publicAssignmentService;
	private final RoleCatalogueConfiguration configuration;

	@Scheduled(fixedDelay = 60 * 1000)
	public void publishAssignmentEvents() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		publicAssignmentService.findAndPublishEvents();
	}
	
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} 1 * * ?")
	public void handleFutureAssignments() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

		publicAssignmentService.publishFutureAssignmentEvents();
	}
}
