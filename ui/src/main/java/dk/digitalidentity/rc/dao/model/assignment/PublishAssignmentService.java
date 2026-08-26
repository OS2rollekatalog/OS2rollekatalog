package dk.digitalidentity.rc.dao.model.assignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.hook.AssignmentHookHandler;
import dk.digitalidentity.rc.dao.model.assignment.hook.HookEvent;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.FutureAssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricAssignmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableAsync
@AllArgsConstructor
public class PublishAssignmentService {
	private final FutureAssignmentService futureAssignmentService;
	private final HistoricAssignmentService historicAssignmentService;
	private final SettingsService settingsService;
	private final List<AssignmentHookHandler> hookHandlers;
	private final TransactionTemplate transactionTemplate;
	private final AssignmentService assignmentService;
	private final UserRoleService userRoleService;

	@PersistenceContext
	private EntityManager entityManager;

	// called when someone adds/removed a SystemRole on a UserRole
	@Async
    @EventListener
	public void userRoleModifiedHandler(UserRoleModifiedEvent userRoleEvent) {
	    transactionTemplate.executeWithoutResult(_ -> {
	        entityManager.setFlushMode(FlushModeType.COMMIT);

			UserRole userRole = userRoleService.getById(userRoleEvent.getUserRoleId());
			if (userRole == null) {
				return;
			}
	
			Set<CurrentAssignment> assignments = assignmentService.getActiveByUserRole(userRole);

			for (CurrentAssignment assignment : assignments) {
				publish(new HookEvent(assignment));
			}
	    });
	}

	// scheduled - runs every minute
	public void findAndPublishEvents() {

		// settingsService to find latest timestamp (default to 24 hours ago on first run)
		LocalDateTime lastRun = settingsService.getAssignmentHookTimestamp();

		// - find all historicAssignments where updatedAt is after (or equals) that timestamp
		//    - future (start_date after today) are put into FutureAssignment
		//    - the rest are published
		LocalDate today = LocalDate.now();
		List<HistoricAssignment> historicAssignments = historicAssignmentService.findByUpdatedAtAfter(lastRun);
		if (historicAssignments.size() == 0) {
			return;
		}
		
		log.info("Processing " + historicAssignments.size() + " assignments");

		// generate new timestamp to store later
		LocalDateTime nextTimestamp = historicAssignments.stream()
		        .map(HistoricAssignment::getUpdatedAt)
		        .filter(Objects::nonNull)
		        .max(Comparator.naturalOrder())
		        .orElse(lastRun);

		// reuse Hibernate 1st level cache across hookHandlers, and ensure a transaction is available
	    transactionTemplate.executeWithoutResult(_ -> {
	        entityManager.setFlushMode(FlushModeType.COMMIT);

			for (HistoricAssignment historicAssignment : historicAssignments) {

				// we do not care about NULL assignments
				if (historicAssignment.getUserRoleId() == null || historicAssignment.getUserRoleId() == 0) {
					continue;
				}
	
				// future assignments are queued for later processing
				if (historicAssignment.getStartDate() != null && historicAssignment.getStartDate().isAfter(today)) {
					FutureAssignment futureAssignment = new FutureAssignment();
					futureAssignment.setHistoricAssignment(historicAssignment);
					futureAssignment.setStartDate(historicAssignment.getStartDate());
					futureAssignmentService.save(futureAssignment);
	
					continue;
				}
				
				publish(new HookEvent(historicAssignment));
			}
	    });

		// store generated timestamp in settings for next run
		settingsService.setAssignmentHookTimestamp(nextTimestamp);
	}
	
	// scheduled, runs every day after midnight to handle those queued for the future
	public void publishFutureAssignmentEvents() {
		log.info("Publishing future assignments that are valid today");

		// do max 10 iterations, if we do 10, then something could be wrong
		int iterations = 0;
		boolean done = false;

		do {
			List<FutureAssignment> futureAssignments = futureAssignmentService.getPending();
			for (FutureAssignment futureAssignment : futureAssignments) {
	
				// if validTo is non-null, then the assignment was cancelled before it became valid
				if (futureAssignment.getHistoricAssignment().getValidTo() == null) {
					publish(new HookEvent(futureAssignment.getHistoricAssignment()));
				}
			}
			
			if (futureAssignments.size() == 0) {
				done = true;
			}
			else if (++iterations >= 10) {
				done = true;
				log.error("We had 10 iterations on future assignments - that is way to many - look into this");
			}
				
			futureAssignmentService.deleteAll(futureAssignments);
		} while (!done);
		
		log.info("Done publishing future assignments");
	}

	private void publish(HookEvent hookEvent) {
		for (AssignmentHookHandler hookHandler : hookHandlers) {
			try {
				hookHandler.handleEvent(hookEvent);
			}
			catch (Exception ex) {
				log.error("AssignmentHookHandler (" + hookHandler.getClass().getName() + ") failed to handle event: " + hookEvent.toString(), ex);
			}
		}
	}
}
