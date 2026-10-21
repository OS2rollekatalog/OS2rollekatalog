package dk.digitalidentity.rc.task.assignment;

import java.util.List;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.assignment.PublishAssignmentService;
import dk.digitalidentity.rc.dao.model.assignment.hook.HookEvent;
import dk.digitalidentity.rc.log.WarnIfSlowerThan;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
@AllArgsConstructor
public class PublishAssignmentEventsTask {
	private final PublishAssignmentService publishAssignmentService;
	private final RoleCatalogueConfiguration configuration;

	@Scheduled(fixedDelay = 60 * 1000)
	@WarnIfSlowerThan(millis = 30 * 1000)
	public void publishAssignmentEvents() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		publishAssignmentService.findAndPublishEvents();
	}
	
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} 1 * * ?")
	@WarnIfSlowerThan(millis = 1 * 60 * 1000)
	public void handleFutureAssignments() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

		log.info("Publishing future assignments that are valid today");

		// do max 10 iterations, if we do 10, then something could be wrong
		int iterations = 0;
		boolean done = false;

		do {
			List<HookEvent> events = publishAssignmentService.publishNextBatch();
			for (HookEvent event : events) {
				publishAssignmentService.publish(event);
			}

			if (events.size() == 0) {
				done = true;
			}
			else if (++iterations >= 10) {
				done = true;
				log.error("We had 10 iterations on future assignments - that is way to many - look into this");
			}
			
			log.info("Published " + events.size() + " future assignments");
		} while (!done);

		log.info("Done publishing future assignments");
	}
}
