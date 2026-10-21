package dk.digitalidentity.rc.event;

import dk.digitalidentity.rc.service.orgunit.OrgUnitNearestManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganisationHierarchyChangedEventHandler {
	private final OrgUnitNearestManagerService orgUnitNearestManagerService;

	// runs after the publisher's transaction has committed, so it never extends the import's locks
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(OrganisationHierarchyChangedEvent event) {
		try {
			orgUnitNearestManagerService.recompute();
		}
		catch (Exception ex) {
			log.error("Failed to recompute nearest managers after an organisation change", ex);
		}
	}
}
