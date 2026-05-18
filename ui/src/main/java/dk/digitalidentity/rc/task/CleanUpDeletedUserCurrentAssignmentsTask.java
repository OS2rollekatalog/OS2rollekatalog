package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.event.AssignmentChangeEventHandlerService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

/**
 * Engangs-oprydning af bagudrettet stale data: brugere der er deleted=true har historisk
 * kunnet akkumulere current_assignment-rækker fordi {@link dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator}
 * ikke filtrerede på sletning. Calculatoren håndhæver nu invariantet ved kilden, men
 * eksisterende rækker skal ryddes op.
 * <p>
 * Bevidst som baggrunds-task frem for Flyway-migration: Galera-clusters er følsomme over for
 * lange transaktioner og store writesets, og en stor DELETE/UPDATE i Flyway kan udløse flow
 * control og pause cluster-skrivninger. Hver task-tick processerer en lille chunk via den
 * normale recalc-pipeline (calculator returnerer tom → saveAllForUsers diff'er rækker væk
 * og lukker historic_assignment med valid_to via updateValidToFor).
 * <p>
 * Engangs-natur: tasken stopper sig selv når backlog er tom og laver ingen flere queries
 * eller log-linjer. Genstart af app gennemtjekker igen — hvis der ikke er noget arbejde,
 * er første tick stille og forbliver stille.
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class CleanUpDeletedUserCurrentAssignmentsTask {

	private static final int CHUNK_SIZE = 20;

	private final RoleCatalogueConfiguration configuration;
	private final CurrentAssignmentService currentAssignmentService;
	private final AssignmentChangeEventHandlerService assignmentChangeEventHandlerService;

	private volatile boolean done = false;
	private boolean started = false;
	private long totalProcessed = 0;

	@Scheduled(fixedDelay = 30_000)
	public void cleanupChunk() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		if (done) {
			return;
		}

		List<String> userUuids = currentAssignmentService.findUuidsOfDeletedUsersWithCurrentAssignments(CHUNK_SIZE);
		if (userUuids.isEmpty()) {
			if (started) {
				log.info("Cleanup of stale current_assignment rows complete: {} deleted users processed in total", totalProcessed);
			}
			done = true;
			return;
		}

		if (!started) {
			log.info("Starting cleanup of stale current_assignment rows for deleted users");
			started = true;
		}

		assignmentChangeEventHandlerService.updateUsers(new HashSet<>(userUuids));
		totalProcessed += userUuids.size();
		log.info("Cleaned up chunk of {} deleted users (running total: {})", userUuids.size(), totalProcessed);
	}
}
