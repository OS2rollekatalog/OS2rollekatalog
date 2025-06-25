package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.kitos.KitosClientService;
import dk.digitalidentity.rc.service.kitos.KitosSyncService;
import dk.kitos.api.model.ItSystemResponseDTO;
import dk.kitos.api.model.ItSystemUsageResponseDTO;
import dk.kitos.api.model.OrganizationUserResponseDTO;
import dk.kitos.api.model.TrackingEventResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class KitosSyncTask {
    private final RoleCatalogueConfiguration configuration;
    private final KitosClientService kitosClientService;
    private final KitosSyncService kitosService;
    private final ItSystemService itSystemService;

    @Scheduled(cron = "${rc.integrations.kitos.cron : 0 0 3/6 ? * *}")
//	@Scheduled(initialDelay = 1000, fixedRate = 100000000)
    public void sync() {
        if (taskDisabled()) {
            return;
        }
        log.info("Starting Kitos synchronisation");
        
        try {
		final UUID municipalUuid = kitosClientService.lookupMunicipalUuid(configuration.getIntegrations().getKitos().getCvr());

		final List<ItSystemUsageResponseDTO> changedItSystemUsages = kitosClientService.fetchChangedItSystemUsage(municipalUuid);
		final boolean reimport = !changedItSystemUsages.isEmpty();
		final List<ItSystemResponseDTO> assocItSystems = changedItSystemUsages.stream()
		    .map(usage -> usage.getSystemContext().getUuid())
		    .map(kitosClientService::fetchItSystem)
		    .filter(Objects::nonNull)
		    .toList();
		final List<ItSystemResponseDTO> changedItSystems = kitosClientService.fetchChangedItSystems(municipalUuid, reimport);

		if (!changedItSystems.isEmpty() || !assocItSystems.isEmpty() || !changedItSystemUsages.isEmpty()) {
			kitosService.syncItSystems(Stream.concat(assocItSystems.stream(), changedItSystems.stream()).toList());
		}

		if (!changedItSystemUsages.isEmpty()) {
		    final List<OrganizationUserResponseDTO> users = kitosClientService.listUsers(municipalUuid);
		    kitosService.syncItSystemUsagesAndUsers(changedItSystemUsages, users);
			itSystemService.syncKitosOwnersAndResponsibles();
		}
	}
	catch (Exception ex) {
		log.error("Error during Kitos synchronisation", ex);
	}

        log.info("Finished Kitos synchronisation");
    }

    @Scheduled(cron = "${rc.integrations.kitos.deletion.cron : 0 10 2 * * ?}")
    public void syncDeletions() {
        if (taskDisabled()) {
            return;
        }
        log.info("Starting Kitos deletion synchronisation");
        
        try {
			final List<TrackingEventResponseDTO> deletedItSystems = kitosClientService.fetchDeletedItSystems(true);
			kitosService.syncDeletedItSystems(deletedItSystems);
		}
		catch (Exception ex) {
			log.error("Error during Kitos deletion synchronisation", ex);
		}

        log.info("Finished Kitos deletion synchronisation");
    }

	@Scheduled(cron = "${rc.integrations.kitos.users.cron : 0 0 0/6 ? * *}")
	public void syncITSystemOwnersAndResponsibles() {
		if (taskDisabled()) {
			return;
		}
		log.info("Starting Kitos IT Systems owners and responsibles synchronisation");

		try {
			itSystemService.syncKitosOwnersAndResponsibles();
		}
		catch (Exception ex) {
			log.error("Error during Kitos IT Systems owners and responsibles synchronisation", ex);
		}

		log.info("Finished Kitos IT Systems owners and responsibles synchronisation");
	}

    private boolean taskDisabled() {
        if (!configuration.getScheduled().isEnabled()) {
            log.info("Scheduling disabled, not doing sync");
            return true;
        }
        if (!configuration.getIntegrations().getKitos().isEnabled()) {
            log.info("Kitos sync not enabled, not doing sync");
            return true;
        }
        return false;
    }


}
