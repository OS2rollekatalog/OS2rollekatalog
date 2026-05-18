package dk.digitalidentity.rc.service.listener;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hibernate post-update listener that queues affected users for recalculation when an
 * {@link OrgUnit}'s manager changes. The queue insert is performed eagerly from inside the
 * same transaction as the OU update, so a rolled-back update rolls back the queue insert
 * along with it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgUnitManagerChangeListener implements PostUpdateEventListener {

	private static final String MANAGER_PROPERTY = "manager";

	private final EntityManagerFactory entityManagerFactory;
	private final UserService userService;

	@PostConstruct
	public void register() {
		entityManagerFactory.unwrap(SessionFactoryImplementor.class)
			.getServiceRegistry()
			.requireService(EventListenerRegistry.class)
			.appendListeners(EventType.POST_UPDATE, this);
	}

	@Override
	public void onPostUpdate(PostUpdateEvent event) {
		if (!(event.getEntity() instanceof OrgUnit ou)) {
			return;
		}
		int idx = managerPropertyIndex(event.getPersister());
		if (idx < 0 || event.getOldState() == null || event.getState() == null) {
			return;
		}
		User oldManager = asUser(event.getOldState()[idx]);
		User newManager = asUser(event.getState()[idx]);
		if (sameUser(oldManager, newManager)) {
			return;
		}
		handleManagerChange(ou, oldManager, newManager);
	}

	@Override
	public boolean requiresPostCommitHandling(EntityPersister persister) {
		return false;
	}

	// Package-private for testing.
	void handleManagerChange(OrgUnit ou, User oldManager, User newManager) {
		Set<String> uuidsToRecalc = new HashSet<>();
		collectAffectedUserUuids(oldManager, ou, uuidsToRecalc);
		collectAffectedUserUuids(newManager, ou, uuidsToRecalc);
		if (uuidsToRecalc.isEmpty()) {
			return;
		}
		// We are inside the same Hibernate flush as the OU update. The simple-queue
		// @EventListener that consumes BulkQueueMessage is @Transactional(REQUIRED) and joins
		// this tx via JdbcTemplate, so the queue insert rolls back together with the OU update
		// on failure. Same pattern as OrganisationImporter.handleParentChange.
		userService.queueMultipleForRecalculation(uuidsToRecalc);
	}

	private static int managerPropertyIndex(EntityPersister persister) {
		String[] names = persister.getPropertyNames();
		for (int i = 0; i < names.length; i++) {
			if (MANAGER_PROPERTY.equals(names[i])) {
				return i;
			}
		}
		return -1;
	}

	private static User asUser(Object value) {
		return value instanceof User user ? user : null;
	}

	private static boolean sameUser(User a, User b) {
		if (a == null && b == null) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		return Objects.equals(a.getUuid(), b.getUuid());
	}

	private static void collectAffectedUserUuids(User manager, OrgUnit ou, Set<String> uuidsToRecalc) {
		if (manager == null) {
			return;
		}
		uuidsToRecalc.add(manager.getUuid());
		if (manager.getManagerSubstitutes() == null) {
			return;
		}
		// Substitutes only inherit manager-/substitute-conditional assignments for the specific OU,
		// so filter on the OU we are reacting to.
		for (ManagerSubstitute relation : manager.getManagerSubstitutes()) {
			if (relation.getOrgUnit() != null
				&& Objects.equals(relation.getOrgUnit().getUuid(), ou.getUuid())
				&& relation.getSubstitute() != null) {
				uuidsToRecalc.add(relation.getSubstitute().getUuid());
			}
		}
	}
}
