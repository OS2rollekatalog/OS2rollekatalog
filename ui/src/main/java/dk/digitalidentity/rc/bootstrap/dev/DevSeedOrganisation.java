package dk.digitalidentity.rc.bootstrap.dev;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedOrganisation {

	public record SeedResult(Map<String, String> orgUnitUuidsByName) {}

	private final OrgUnitService orgUnitService;

	public SeedResult seed() {
		log.info("Seeding organisation...");
		HashMap<String, String> orgUnitUuidsByName = new HashMap<>();
		saveTree(DevDataDefinitions.ROOT, null, orgUnitUuidsByName);
		return new SeedResult(orgUnitUuidsByName);
	}

	private void saveTree(DevDataDefinitions.OrgUnitDef def, OrgUnit parent,
			HashMap<String, String> orgUnitUuidsByName) {
		OrgUnit ou = new OrgUnit();
		ou.setUuid(UUID.randomUUID().toString());
		ou.setName(def.name());
		ou.setActive(true);
		ou.setLevel(OrgUnitLevel.NONE);
		ou.setParent(parent);
		OrgUnit saved = orgUnitService.save(ou);
		orgUnitUuidsByName.put(saved.getName(), saved.getUuid());
		for (DevDataDefinitions.OrgUnitDef child : def.children()) {
			saveTree(child, saved, orgUnitUuidsByName);
		}
	}

}
