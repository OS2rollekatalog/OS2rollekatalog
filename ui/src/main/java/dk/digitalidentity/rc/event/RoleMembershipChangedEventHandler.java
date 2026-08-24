package dk.digitalidentity.rc.event;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.cics.KspCicsService;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.SimpleMessageHandler;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reagerer på RoleMembershipChanged-beskeder, der udsendes fra CurrentAssignment-genberegningen
 * (se {@link AssignmentChangeEventHandlerService}), ved at markere de berørte roller dirty i de
 * medlemskabs-drevne downstream-systemer: AD ({@code dirty_ad_groups}) og KSP-CICS
 * ({@code dirty_ksp_cics_user_profiles}).
 * <p>
 * Dette er en delta-præcis, transaktionelt holdbar erstatning for den hensigts-baserede markering
 * i {@code LdapUpdaterHook}/{@code KspCicsUpdaterHook}: fordi signalet udledes af det faktiske
 * delete/create-delta i {@code CurrentAssignment}, fanges også fjernelser via arv/rollebuket/weight,
 * som den hensigts-baserede markering kunne misse (issue #83).
 * <p>
 * Markeringen er idempotent: forbrugerne genberegner det faktiske medlemskab ved flush (AD-endpoint
 * læser {@code CurrentAssignment}; KSP-CICS kalder {@code getActiveByUserRole}), så et gen-leveret
 * event er harmløst. Dedup sker desuden på {@code messageId = userRoleId}.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RoleMembershipChangedEventHandler implements SimpleMessageHandler {
	public static final String ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER = "role_membership_changed_queue";

	private final UserRoleService userRoleService;
	private final PendingADUpdateService pendingADUpdateService;
	private final KspCicsService kspCicsService;

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Set<String> activeQueues() {
		return Set.of(ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER);
	}

	@Override
	public boolean handles(QueueMessage message) {
		return ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER.equals(message.getQueue());
	}

	@Override
	@Transactional
	public boolean handleMessage(QueueMessage queueMessage) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final Long userRoleId = userRoleIdOf(queueMessage);
		if (userRoleId != null) {
			markDirty(userRoleService.getById(userRoleId), userRoleId);
		}
		return true;
	}

	@Override
	@Transactional
	public boolean handleMessages(List<QueueMessage> queueMessages) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final Set<Long> userRoleIds = queueMessages.stream()
			.map(this::userRoleIdOf)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		// Batch-load to avoid one SELECT per role - a large OU/role-group change can touch many roles
		final Set<UserRole> userRoles = userRoleService.findAllByIdIn(userRoleIds);
		for (UserRole userRole : userRoles) {
			markDirty(userRole, userRole.getId());
		}

		if (log.isDebugEnabled() && userRoles.size() < userRoleIds.size()) {
			final Set<Long> missing = new HashSet<>(userRoleIds);
			userRoles.forEach(ur -> missing.remove(ur.getId()));
			log.debug("UserRoles {} no longer exist, skipping membership-changed marking", missing);
		}
		return true;
	}

	@Override
	public boolean handleFailedMessage(QueueMessage message, Exception exception) {
		// getById returnerer null for manglende roller (håndteres i markDirty), så her opstår kun
		// transiente/uventede fejl. Behold beskeden som FAILED frem for at slette den, så fejlen er synlig.
		return false;
	}

	private Long userRoleIdOf(QueueMessage queueMessage) {
		final RoleMembershipChangedMessage message = JsonSimpleMessage.fromJson(queueMessage.getBody(), RoleMembershipChangedMessage.class);
		if (message == null || message.getUserRoleId() == null) {
			log.warn("Received role membership changed message with null body or missing userRoleId, skipping");
			return null;
		}
		return message.getUserRoleId();
	}

	private void markDirty(UserRole userRole, Long userRoleId) {
		if (userRole == null) {
			// Rollen kan være slettet i mellemtiden. Selve sletningen af en UserRole håndteres ad
			// anden vej (rolle-modellering), så her er det sikkert blot at springe over.
			log.debug("UserRole {} no longer exists, skipping membership-changed marking", userRoleId);
			return;
		}

		// Hver service filtrerer selv på sin egen ItSystemType (AD hhv. KSPCICS), så det er sikkert
		// at kalde begge uanset hvilket system rollen tilhører.
		pendingADUpdateService.addUserRoleToQueue(userRole);
		kspCicsService.addUserRoleToQueue(userRole);
	}
}
