package dk.digitalidentity.rc.service.kitos;

import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.kitos.exception.KitosSynchronizationException;
import dk.kitos.api.ApiV2DeltaFeedApi;
import dk.kitos.api.ApiV2ItContractApi;
import dk.kitos.api.ApiV2ItSystemApi;
import dk.kitos.api.ApiV2ItSystemUsageApi;
import dk.kitos.api.ApiV2ItSystemUsageRoleTypeApi;
import dk.kitos.api.ApiV2OrganizationApi;
import dk.kitos.api.model.ItContractResponseDTO;
import dk.kitos.api.model.ItSystemResponseDTO;
import dk.kitos.api.model.ItSystemUsageResponseDTO;
import dk.kitos.api.model.OrganizationResponseDTO;
import dk.kitos.api.model.OrganizationUserResponseDTO;
import dk.kitos.api.model.TrackingEventResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static dk.digitalidentity.rc.service.kitos.KitosConstants.IT_SYSTEM_DELETION_OFFSET_USAGE_SETTING_KEY;
import static dk.digitalidentity.rc.service.kitos.KitosConstants.IT_SYSTEM_ENTITY_TYPE;
import static dk.digitalidentity.rc.service.kitos.KitosConstants.IT_SYSTEM_OFFSET_SETTING_KEY;
import static dk.digitalidentity.rc.service.kitos.KitosConstants.IT_SYSTEM_USAGE_OFFSET_SETTING_KEY;
import static dk.digitalidentity.rc.service.kitos.KitosConstants.KITOS_DELTA_START_FROM;
import static dk.digitalidentity.rc.service.kitos.KitosConstants.KITOS_DELTA_START_FROM_OFFSET;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitosClientService {
    private final ApiV2ItSystemApi itSystemApi;
    private final ApiV2ItSystemUsageApi itSystemUsageApi;
    private final ApiV2OrganizationApi organizationApi;
    private final ApiV2ItSystemUsageRoleTypeApi systemUsageRoleTypeApi;
    private final ApiV2ItContractApi contractApi;
    private final ApiV2DeltaFeedApi deltaFeedApi;
    private final SettingsService settingsService;

    public UUID lookupMunicipalUuid(final String cvr) {
        final List<OrganizationResponseDTO> organizations = organizationApi.getManyOrganizationV2GetOrganizations(null, null, cvr, null, null, null, 0, 1);
        if (organizations == null || organizations.isEmpty()) {
            throw new KitosSynchronizationException("Could not lookup uuid for cvr: " + cvr);
        }
        return organizations.get(0).getUuid();
    }

    public List<OrganizationUserResponseDTO> listUsers(final UUID municipalUuid) {
        final List<OrganizationUserResponseDTO> allUsers = new ArrayList<>();
        List<OrganizationUserResponseDTO> currentUsers;
        int page = 0;
        do {
            currentUsers = organizationApi.getManyOrganizationV2GetOrganizationUsers(municipalUuid, null, null, null, null, page++, KitosConstants.PAGE_SIZE);
            allUsers.addAll(currentUsers);
        } while (currentUsers.size() == KitosConstants.PAGE_SIZE && page < KitosConstants.MAX_PAGE_REQUEST);
        return allUsers;
    }

    public ItSystemResponseDTO fetchItSystem(final UUID kitosUuid) {
        log.info("Fetching it-system {}", kitosUuid);
        try {
            return itSystemApi.getSingleItSystemV2GetItSystem(kitosUuid);
        } catch (final HttpClientErrorException ex) {
            log.warn("Could not fetch it-system with uuid: {}", kitosUuid, ex);
        }
        return null;
    }

    /**
     * Fetch all deleted it-systems
     */
    public List<TrackingEventResponseDTO> fetchDeletedItSystems(final boolean reimport) {
        return deltaFetch(IT_SYSTEM_DELETION_OFFSET_USAGE_SETTING_KEY,
            pageAndOffset -> deltaFeedApi.getManyDeltaFeedV2GetDeletedObjects(IT_SYSTEM_ENTITY_TYPE, reimport ? KITOS_DELTA_START_FROM_OFFSET : pageAndOffset.getValue().plusNanos(1000L), pageAndOffset.getKey(), KitosConstants.PAGE_SIZE),
            TrackingEventResponseDTO::getOccurredAtUtc
        );
    }

    /**
     * Fetch all it-systems in use by the current municipal
     */
    public List<ItSystemResponseDTO> fetchChangedItSystems(final UUID municipalUuid, final boolean reimport) {
        return deltaFetch(IT_SYSTEM_OFFSET_SETTING_KEY,
            pageAndOffset -> itSystemApi.getManyItSystemV2GetItSystems(null, null, null, null, null,
                false, reimport ? KITOS_DELTA_START_FROM_OFFSET : pageAndOffset.getValue().plusNanos(1000L), municipalUuid, null, null, pageAndOffset.getKey(), KitosConstants.PAGE_SIZE),
            ItSystemResponseDTO::getLastModified
        );
    }

    /**
     * Fetch all it-system-usages for the current municipal
     */
    public List<ItSystemUsageResponseDTO> fetchChangedItSystemUsage(final UUID municipalUuid) {
        return deltaFetch(IT_SYSTEM_USAGE_OFFSET_SETTING_KEY,
            pageAndOffset -> itSystemUsageApi.getManyItSystemUsageV2GetItSystemUsages(municipalUuid, null, null, null, null,
                null, pageAndOffset.getValue().plusNanos(1000L), null, pageAndOffset.getKey(), KitosConstants.PAGE_SIZE),
            ItSystemUsageResponseDTO::getLastModified
        );
    }

    public <T> List<T> deltaFetch(final String settingsKey,
                                  final Function<Pair<Integer, OffsetDateTime>, List<T>> getter,
                                  final Function<T, OffsetDateTime> getLastModified) {
        final ZonedDateTime syncFrom = settingsService.getZonedDateTime(settingsKey, KITOS_DELTA_START_FROM);
        final OffsetDateTime offsetDateTime = syncFrom.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        final List<T> allItSystemUsages = new ArrayList<>();
        OffsetDateTime newestChange = offsetDateTime;
        List<T> currentEntitites;
        int page = 0;
        do {
            throttle();
            currentEntitites = getter.apply(Pair.of(page++, offsetDateTime));
            if (!currentEntitites.isEmpty()) {
                newestChange = getLastModified.apply(currentEntitites.get(currentEntitites.size()-1));
            }
            allItSystemUsages.addAll(currentEntitites);
        } while (currentEntitites.size() == KitosConstants.PAGE_SIZE && page < KitosConstants.MAX_PAGE_REQUEST);
        settingsService.setZonedDateTime(settingsKey, newestChange.toZonedDateTime());
        return allItSystemUsages;
    }

    private static void throttle() {
        try {
            Thread.sleep(100);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
