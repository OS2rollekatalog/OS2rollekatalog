package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedItSystems {

	public record SeedResult(Map<String, Long> itSystemIdsByIdentifier, Map<String, Long> systemRoleIdsByIdentifier) {}

	private final ConstraintTypeService constraintTypeService;
	private final ItSystemService itSystemService;
	private final SystemRoleService systemRoleService;
	private final DomainService domainService;

	public SeedResult seed() {
		log.info("Seeding IT systems...");

		List<ConstraintTypeSupport> kombitConstraints = createConstraintTypes(DevDataDefinitions.KOMBIT_CONSTRAINT_TYPES);
		List<ConstraintTypeSupport> nemloginConstraints = createConstraintTypes(DevDataDefinitions.NEMLOGIN_CONSTRAINT_TYPES);

		HashMap<String, Long> itSystemIdsByIdentifier = new HashMap<>();
		HashMap<String, Long> systemRoleIdsByIdentifier = new HashMap<>();

		for (DevDataDefinitions.ItSystemDef def : DevDataDefinitions.IT_SYSTEMS) {
			ItSystem itSystem = new ItSystem();
			itSystem.setIdentifier(def.identifier());
			itSystem.setName(def.name());
			itSystem.setSystemType(def.systemType());
			itSystem.setCanEditThroughApi(def.canEditThroughApi());
			if (def.hasDomain()) {
				itSystem.setDomain(domainService.getPrimaryDomain());
			}
			itSystem = itSystemService.save(itSystem);
			itSystemIdsByIdentifier.put(itSystem.getIdentifier(), itSystem.getId());

			List<ConstraintTypeSupport> constraints = switch (def.systemType()) {
				case ItSystemType.KOMBIT -> kombitConstraints;
				case ItSystemType.NEMLOGIN -> nemloginConstraints;
				default -> List.of();
			};

			for (DevDataDefinitions.SystemRoleDef roleDef : def.roles()) {
				SystemRole role = new SystemRole();
				role.setIdentifier(roleDef.identifier());
				role.setName(roleDef.name());
				role.setRoleType(roleDef.roleType());
				role.setItSystem(itSystem);
				role.setSupportedConstraintTypes(new ArrayList<>(constraints));
				SystemRole savedRole = systemRoleService.save(role);
				systemRoleIdsByIdentifier.put(savedRole.getIdentifier(), savedRole.getId());
			}
		}

		return new SeedResult(itSystemIdsByIdentifier, systemRoleIdsByIdentifier);
	}

	private List<ConstraintTypeSupport> createConstraintTypes(List<DevDataDefinitions.ConstraintTypeDef> defs) {
		return defs.stream().map(def -> {
			ConstraintType ct = new ConstraintType();
			ct.setEntityId(def.entityId());
			ct.setUuid(UUID.randomUUID().toString());
			ct.setName(def.name());
			ct.setUiType(def.uiType());
			ct.setRegex(def.regex());
			ct = constraintTypeService.save(ct);

			ConstraintTypeSupport support = new ConstraintTypeSupport();
			support.setConstraintType(ct);
			support.setMandatory(false);
			return support;
		}).toList();
	}

}
