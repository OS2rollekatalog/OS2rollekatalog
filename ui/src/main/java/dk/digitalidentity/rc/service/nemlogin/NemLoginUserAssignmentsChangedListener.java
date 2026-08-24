package dk.digitalidentity.rc.service.nemlogin;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.event.UserAssignmentsChangedListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bemærk: denne bean er bevidst IKKE @ConditionalOnProperty. Under CRaC tages checkpointet på
 * build-tid med default-config (hvor nemlogin er disabled), og @Conditional re-evalueres aldrig
 * på restore (CracAwareness re-binder kun config-værdier, ikke bean-grafen). En betinget bean
 * ville derfor blive frosset ude og listeneren aldrig køre, selv om nemlogin er enabled i prod.
 * Vi gater i stedet på runtime mod den re-bundne RoleCatalogueConfiguration — samme mønster som
 * NemLoginUpdateTask, der netop derfor overlever CRaC.
 */
@RequiredArgsConstructor
@Component
public class NemLoginUserAssignmentsChangedListener implements UserAssignmentsChangedListener {

	private final NemLoginService nemLoginService;
	private final RoleCatalogueConfiguration configuration;

	@Override
	public void onUserAssignmentsChanged(User user) {
		if (!configuration.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}

		nemLoginService.syncUserRoleAssignments(user);
	}
}
