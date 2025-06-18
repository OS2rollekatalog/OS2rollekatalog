package dk.digitalidentity.rc.service.kitos;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.Kitos;
import dk.digitalidentity.rc.dao.model.KitosITSystem;
import dk.digitalidentity.rc.dao.model.KitosITSystemUser;
import dk.digitalidentity.rc.dao.model.enums.KitosRole;
import dk.digitalidentity.rc.service.KitosITSystemService;
import dk.kitos.api.model.ItSystemResponseDTO;
import dk.kitos.api.model.ItSystemUsageResponseDTO;
import dk.kitos.api.model.OrganizationUserResponseDTO;
import dk.kitos.api.model.TrackingEventResponseDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitosSyncService {
    private final KitosITSystemService kitosITSystemService;
    private final RoleCatalogueConfiguration configuration;

    @Transactional
    public void syncItSystems(final List<ItSystemResponseDTO> changedItSystems) {
        changedItSystems.forEach(this::syncSingleItSystem);
    }

    private void syncSingleItSystem(final ItSystemResponseDTO responseDTO) {
        final Optional<KitosITSystem> itSystem = kitosITSystemService.findByKitosUuid(responseDTO.getUuid());
        if (itSystem.isPresent()) {
            updateKitosITSystem(itSystem.get(), responseDTO);
        } else {
            createKitosITSystem(responseDTO);
        }
    }

    private void updateKitosITSystem(final KitosITSystem itSystem, final ItSystemResponseDTO responseDTO) {
        itSystem.setName(responseDTO.getName());
    }

    private void createKitosITSystem(final ItSystemResponseDTO responseDTO) {
        final KitosITSystem kitosITSystem = new KitosITSystem();
        kitosITSystem.setName(responseDTO.getName());
        kitosITSystem.setKitosUuid(responseDTO.getUuid());
        kitosITSystemService.save(kitosITSystem);
    }

    @Transactional
    public void syncDeletedItSystems(final List<TrackingEventResponseDTO> deletionEvents) {
        deletionEvents.stream().map(TrackingEventResponseDTO::getEntityUuid).forEach(this::syncItSystemDeletion);
    }

    private void syncItSystemDeletion(final UUID itSystemUuid) {
        kitosITSystemService.deleteByKitosUuid(itSystemUuid);
    }

    @Transactional
    public void syncItSystemUsagesAndUsers(List<ItSystemUsageResponseDTO> changedItSystemUsages, List<OrganizationUserResponseDTO> users) {
        changedItSystemUsages.forEach(c -> syncSingleUsage(c, users));
    }

    private void syncSingleUsage(ItSystemUsageResponseDTO itSystemUsageResponseDTO, List<OrganizationUserResponseDTO> users) {
        final Optional<KitosITSystem> itSystem = kitosITSystemService.findByKitosUuid(itSystemUsageResponseDTO.getSystemContext().getUuid());
		itSystem.ifPresent(kitosITSystem -> updateKitosITSystemWithUsers(kitosITSystem, itSystemUsageResponseDTO, users));
	}

    private void updateKitosITSystemWithUsers(KitosITSystem kitosITSystem, ItSystemUsageResponseDTO itSystemUsageResponseDTO, List<OrganizationUserResponseDTO> users) {
        final boolean valid = itSystemUsageResponseDTO.getGeneral().getValidity().getValid() == null || itSystemUsageResponseDTO.getGeneral().getValidity().getValid();
        if (!valid) {
            return;
        }

        if (kitosITSystem.getKitosUsers() == null) {
            kitosITSystem.setKitosUsers(new ArrayList<>());
        }

        handleUsersForRole(KitosRole.SYSTEM_OWNER, configuration.getIntegrations().getKitos().getSystemOwnerRoleUUID(), kitosITSystem, users, itSystemUsageResponseDTO);
        handleUsersForRole(KitosRole.SYSTEM_RESPONSIBLE, configuration.getIntegrations().getKitos().getSystemResponsibleRoleUUID(), kitosITSystem, users, itSystemUsageResponseDTO);
    }

    private void handleUsersForRole(KitosRole kitosRole, String roleUUID, KitosITSystem kitosITSystem, List<OrganizationUserResponseDTO> users, ItSystemUsageResponseDTO itSystemUsageResponseDTO) {
        Set<UUID> usersWithRoleUUIDs = itSystemUsageResponseDTO.getRoles().stream()
                .filter(r -> roleUUID.equalsIgnoreCase(r.getRole().getUuid().toString()))
                .map(r -> r.getUser().getUuid())
                .collect(Collectors.toSet());

        List<OrganizationUserResponseDTO> userDTOs = users.stream()
                .filter(u -> usersWithRoleUUIDs.contains(u.getUuid()))
                .toList();

        List<KitosITSystemUser> kitosUsersWithRole = kitosITSystem.getKitosUsers().stream()
                .filter(u -> u.getRole().equals(kitosRole)).collect(Collectors.toList());

        for (OrganizationUserResponseDTO userDTO : userDTOs) {
            kitosUsersWithRole.stream()
                    .filter(u -> u.getKitosUuid().equals(userDTO.getUuid())).findFirst()
                    .ifPresentOrElse(
                            u -> updateKitosITSystemUser(u, userDTO),
                            () -> createKitosITSystemUser(userDTO, kitosITSystem, kitosRole)
                    );
        }

        kitosITSystem.getKitosUsers().removeIf(u -> u.getRole().equals(kitosRole) && !usersWithRoleUUIDs.contains(u.getKitosUuid()));
    }

    private void createKitosITSystemUser(OrganizationUserResponseDTO userDTO, KitosITSystem kitosITSystem, KitosRole kitosRole) {
        KitosITSystemUser kitosITSystemUser = new KitosITSystemUser();
        kitosITSystemUser.setKitosUuid(userDTO.getUuid());
        kitosITSystemUser.setName(userDTO.getName());
        kitosITSystemUser.setRole(kitosRole);
        kitosITSystemUser.setEmail(userDTO.getEmail());
        kitosITSystemUser.setKitosITSystem(kitosITSystem);
        kitosITSystem.getKitosUsers().add(kitosITSystemUser);
    }

    private void updateKitosITSystemUser(KitosITSystemUser kitosUser, OrganizationUserResponseDTO userDTO) {
        kitosUser.setEmail(userDTO.getEmail());
        kitosUser.setName(userDTO.getName());
    }
}
