package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.extern.slf4j.Slf4j;

// skal kun anvendes til initiel migrering af data
@Slf4j
@RequireApiOrganisationRole
@RestController
public class KleLoadApi {

	@Autowired
	private KleService kleService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	record KleLoadset(List<String> init, List<String> remove, List<String> add) {};
	record KleLoad(String uuid, KleLoadset kle) {};
	
	@PostMapping("/api/kleload")
	public ResponseEntity<?> loadKle(@RequestBody List<KleLoad> kleLoad) {
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		
		// make sure no existing KLE has been loaded into organisation
		for (OrgUnit orgUnit : orgUnits) {
			if (orgUnit.getKles().size() > 0) {
				log.warn("Cannot perform load if any KLE has been assigned to OUs already");
				return ResponseEntity.badRequest().build();
			}
		}

		List<Kle> kles = kleService.findAll();
		log.info("Found " + kles.size() + " kles in DB");

		Map<String, Kle> kleMap = kles.stream().collect(Collectors.toMap(Kle::getCode, Function.identity()));
		List<String> sortedKles = new ArrayList<String>(kles.stream().map(k -> k.getCode()).collect(Collectors.toList()));
		Collections.sort(sortedKles);

		List<OrgUnit> orgUnitsToSave = new ArrayList<>();

		Map<String, OrgUnit> orgUnitMap = orgUnits.stream().collect(Collectors.toMap(OrgUnit::getUuid, Function.identity()));

		log.info("Performing KLE load on " + kleLoad.size() + " orgUnits");
		
		// apply initial
		for (KleLoad loadSet : kleLoad) {
			OrgUnit orgUnit = orgUnitMap.get(loadSet.uuid());
			if (orgUnit == null) {
				log.warn("Failed to find OrgUnit with uuid: " + loadSet.uuid());
				continue;
			}
						
			List<String> kleValues = loadSet.kle.init();
			
			if (kleValues.size() > 0) {
				log.info("Initial on " + orgUnit.getName() + " with " + kleValues.size() + " KLE values");
	
				for (String kleValue : kleValues) {
					List<String> klesToAddToOu = expandKle(kleValue, sortedKles, kleMap);
	
					if (klesToAddToOu != null && klesToAddToOu.size() > 0) {
						addKleToOuRecursive(klesToAddToOu, orgUnit, orgUnitsToSave);
					}
				}
			}
		}

		// apply substractions
		for (KleLoad loadSet : kleLoad) {
			OrgUnit orgUnit = orgUnitMap.get(loadSet.uuid());
			if (orgUnit == null) {
				log.warn("Failed to find OrgUnit with uuid: " + loadSet.uuid());
				continue;
			}
			
			List<String> kleValues = loadSet.kle.remove();

			if (kleValues.size() > 0) {
				log.info("Subtracting on " + orgUnit.getName() + " with " + kleValues.size() + " KLE values");
				
				for (String kleValue : kleValues) {
					List<String> klesToRemoveFromOu = expandKle(kleValue, sortedKles, kleMap);
	
					if (klesToRemoveFromOu != null && klesToRemoveFromOu.size() > 0) {
						removeKleFromOu(klesToRemoveFromOu, orgUnit, sortedKles);
					}
				}
			}
		}

		// apply additions
		for (KleLoad loadSet : kleLoad) {
			OrgUnit orgUnit = orgUnitMap.get(loadSet.uuid());
			if (orgUnit == null) {
				log.warn("Failed to find OrgUnit with uuid: " + loadSet.uuid());
				continue;
			}
			
			List<String> kleValues = loadSet.kle.add();

			if (kleValues.size() > 0) {
				log.info("Adding on " + orgUnit.getName() + " with " + kleValues.size() + " KLE values");
	
				for (String kleValue : kleValues) {
					List<String> klesToAddToOu = expandKle(kleValue, sortedKles, kleMap);
	
					if (klesToAddToOu != null && klesToAddToOu.size() > 0) {
						addKleToOu(klesToAddToOu, orgUnit);
					}
				}
			}
		}

		// finally save to DB
		if (orgUnitsToSave.size() > 0) {
			// TODO: simplify - due to negation and adding, some OU's have ALL children of a KLE added, and we can remove the children and just add the parent KLE
			log.info("Saving " + orgUnitsToSave.size() + " orgunits");
			orgUnitService.save(orgUnitsToSave);
		}
		
		return ResponseEntity.ok("");
	}

	private void addKleToOu(List<String> klesToAddToOu, OrgUnit orgUnit) {
		if (klesToAddToOu == null || klesToAddToOu.size() == 0) {
			return;
		}

		for (String kle : klesToAddToOu) {
			boolean add = true;
			
			for (KLEMapping mapping : orgUnit.getKles()) {
				if (mapping.getCode().equals(kle)) {
					add = false;
					break;
				}
			}
			
			if (add) {
				KLEMapping mapping = new KLEMapping();
				mapping.setAssignmentType(KleType.PERFORMING);
				mapping.setCode(kle);
				mapping.setOrgUnit(orgUnit);
				
				orgUnit.getKles().add(mapping);
			}
		}
	}

	private void removeKleFromOu(List<String> klesToRemoveFromOu, OrgUnit orgUnit, List<String> sortedKles) {
		if (klesToRemoveFromOu == null || klesToRemoveFromOu.size() == 0) {
			return;
		}

		List<String> toAdd = new ArrayList<String>();
		for (String kle : klesToRemoveFromOu) {
			for (Iterator<KLEMapping> iterator = orgUnit.getKles().iterator(); iterator.hasNext();) {
				KLEMapping kleMapping = iterator.next();

				// we use startsWith for recursive removal downwards (e.g. removing 02.00 removes everything in 02.00.*)
				if (kleMapping.getCode().startsWith(kle)) {
					iterator.remove();
					continue;
				}
				
				// we then look upwards, and expand then remove if a parent KLE contains this kle (e.g. removing 02.00 should remove 02 and expand it instead)
				if (kle.startsWith(kleMapping.getCode()))  {
					iterator.remove();
					toAdd.addAll(getChildrenExcept(kleMapping.getCode(), kle, sortedKles));
					continue;
				}
			}
		}
		
		if (toAdd.size() > 0) {
			addKleToOu(toAdd, orgUnit);
		}
	}

	private List<String> getChildrenExcept(String childrenFromThisKle, String butNotThisKle, List<String> sortedKles) {
		List<String> result = new ArrayList<>();
		
		for (String kle : sortedKles) {
			// only interested in children
			if (!kle.startsWith(childrenFromThisKle)) {
				continue;
			}
			
			// and ONLY children
			if (kle.equals(childrenFromThisKle)) {
				continue;
			}
			
			// but not this kle (and it's children)
			if (butNotThisKle.startsWith(kle)) {
				continue;
			}
			
			result.add(kle);
		}

		return result;
	}

	// apply to this OU and all children - take duplicates into consideration
	private void addKleToOuRecursive(List<String> klesToAddToOu, OrgUnit orgUnit, List<OrgUnit> orgUnitsToSave) {
		if (klesToAddToOu == null || klesToAddToOu.size() == 0) {
			return;
		}
		
		// add them to OU
		addKleToOu(klesToAddToOu, orgUnit);
		
		// flag OU for saving
		if (!orgUnitsToSave.contains(orgUnit)) {
			orgUnitsToSave.add(orgUnit);
		}
		
		// look at children
		if (orgUnit.getChildren() != null && orgUnit.getChildren().size() > 0) {
			for (OrgUnit child : orgUnit.getChildren()) {

				// add KLE to child
				addKleToOuRecursive(klesToAddToOu, child, orgUnitsToSave);
				
				// flag child for saving
				if (!orgUnitsToSave.contains(child)) {
					orgUnitsToSave.add(child);
				}
			}
		}
	}

	// handle intervals and lookup in list of existing Kles from DB
	private List<String> expandKle(String kleValue, List<String> sortedKles, Map<String, Kle> kleMap) {
		// not an interval, just return a singleton list
		if (!kleValue.contains("-")) {
			Kle kle = kleMap.get(kleValue);
			if (kle != null) {
				return Collections.singletonList(kle.getCode());
			}
			
			return null;
		}

		// we have an interval, deal with it
		String[] tokens = kleValue.split("-");

		Kle startKle = kleMap.get(tokens[0]);
		if (startKle == null) {
			log.warn("Could not find start KLE with value " + tokens[0]);
			return null;
		}
		
		Kle stopKle = kleMap.get(tokens[1]);

		if (stopKle == null) {
			log.warn("Could not find stop KLE with value " + tokens[1]);
			return null;
		}
		
		if (startKle.getCode().length() != stopKle.getCode().length()) {
			log.warn("Cannot load mismatching interval: " + startKle.getCode() + " - " + stopKle.getCode());
			return null;
		}
		
		int length = startKle.getCode().length();
		
		List<String> result = new ArrayList<String>();
		boolean adding = false;
		for (String kle : sortedKles) {
			if (Objects.equals(kle, startKle.getCode())) {
				adding = true;
			}

			if (!adding) {
				continue;
			}
			
			boolean stop = false;
			if (Objects.equals(kle, stopKle.getCode())) {
				stop = true;
			}
			
			// skip sub-kle's when looking at intervals
			if (kle.length() != length) {
				continue;
			}
			
			// within range, so add
			Kle kleResult = kleMap.get(kle);
			if (kleResult != null) {
				result.add(kleResult.getCode());			
			}
			else {
				log.warn("Failed to find " + kle);
			}
			
			if (stop) {
				break;
			}
		}

		return result;
	}
}
