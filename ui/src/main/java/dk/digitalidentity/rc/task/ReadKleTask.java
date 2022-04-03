package dk.digitalidentity.rc.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.KleDao;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.os2sync.OS2syncKlassifikationService;
import dk.kleonline.xml.EmneKomponent;
import dk.kleonline.xml.GruppeKomponent;
import dk.kleonline.xml.HovedgruppeKomponent;
import dk.kleonline.xml.KLEEmneplanKomponent;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@EnableAsync
@Slf4j
public class ReadKleTask {
	private static Map<String, String> kleCacheMap = new HashMap<>();

	@Autowired
	@Qualifier("defaultRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private KleDao kleDao;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private KleService kleService;

	@Autowired
	private OS2syncKlassifikationService os2syncKlassifikationService;

	@Async
	public void init() {
		if (configuration.getScheduled().isEnabled() && kleDao.countByActiveTrue() == 0) {
			parse();
		}
		else {
			// even non scheduled instances should populate the cache
			loadCache();
		}
	}

	private void loadCache() {
		Map<String, String> newKleCacheMap = new HashMap<>();

		List<Kle> kleList = kleDao.findAll();		
		for (Kle kle : kleList) {
			newKleCacheMap.put(kle.getCode(), kle.getName());
		}
		
		kleCacheMap = newKleCacheMap;
	}

	// Run every Saturday at random point between 22:00-22:30
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(30)} 22 * * SAT")
	public synchronized void reloadCache() {
		if (configuration.getScheduled().isEnabled()) {
			return; // do not reload cache on the instance that is running the scheduled task
		}
		
		log.info("Refreshing KLE cache");

		loadCache();
	}

	// Run every Saturday at random point between 21:00-21:30
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(30)} 21 * * SAT")
	public synchronized void parse() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		if (configuration.getIntegrations().getKle().isUseOS2sync() && !StringUtils.isEmpty(configuration.getIntegrations().getKle().getOs2SyncUrl())) {
			readFromKOMBIT();
		}
		else {
			readFromKleOnline();
		}
	}

	private void readFromKOMBIT() {
		log.info("Fetching KLE from KOMBIT and refreshing cache");

		Map<String, String> newCacheMap = new HashMap<>();
		Collection<Kle> updatedKleList = os2syncKlassifikationService.load(configuration.getCustomer().getCvr());
		List<Kle> kleList = kleService.findAll();

		for (Kle updatedKle : updatedKleList) {
			boolean found = false;
			boolean changes = false;

			for (Iterator<Kle> iterator = kleList.iterator(); iterator.hasNext(); ) {
				Kle kle = iterator.next();

				if (kle.getCode().equals(updatedKle.getCode())) {
					found = true;

					if (!Objects.equals(kle.getName(), updatedKle.getName())) {
						changes = true;
					}

					if (kle.isActive() != updatedKle.isActive()) {
						changes = true;
					}
					
					iterator.remove();
					break;
				}
			}

			if (found && changes) {
				// fetch from DB to bypass cache
				Kle kle = kleService.getByCode(updatedKle.getCode());
				kle.setName(updatedKle.getName());
				kle.setActive(updatedKle.isActive());

				kleService.save(kle);
			}
			else if (!found) {
				kleService.save(updatedKle);
			}

			newCacheMap.put(updatedKle.getCode(), updatedKle.getName());
		}

		// Deactivate whatever is left in the list
		for (Kle inactiveKle : kleList) {
			Kle kle = kleService.getByCode(inactiveKle.getCode());
			kle.setActive(false);

			kleService.save(kle);
		}

		kleCacheMap = newCacheMap;
	}
	
	private void readFromKleOnline() {
		log.info("Fetching KLE from kle-online and refreshing cache");

		Map<String, String> newCacheMap = new HashMap<>();
		List<Kle> updatedKleList = getUpdatedKleList();
		List<Kle> kleList = kleDao.findAll();

		for (Kle updatedKle : updatedKleList) {
			boolean found = false;
			boolean nameChange = false;
			boolean activate = false;

			for (Iterator<Kle> iterator = kleList.iterator(); iterator.hasNext();) {
				Kle kle = iterator.next();

				if (kle.getCode().equals(updatedKle.getCode())) {
					found = true;

					if (!kle.getName().equals(updatedKle.getName())) {
						nameChange = true;
					}

					if (!kle.isActive()) {
						activate = true;
					}

					iterator.remove();
					break;
				}
			}

			if (found && (nameChange || activate)) {
				Kle kle = kleDao.getByCode(updatedKle.getCode());
				kle.setName(updatedKle.getName());
				kle.setActive(true);

				kleDao.save(kle);
			}
			else if (!found) {
				kleDao.save(updatedKle);
			}
			
			newCacheMap.put(updatedKle.getCode(), updatedKle.getName());
		}

		// Deactivate whatever is left in the list
		for (Kle inactiveKle : kleList) {
			Kle kle = kleDao.getByCode(inactiveKle.getCode());
			kle.setActive(false);

			kleDao.save(kle);
		}
		
		kleCacheMap = newCacheMap;
	}
	
	public List<Kle> getUpdatedKleList() {
		List<Kle> updatedKleList = new ArrayList<>();

		HttpEntity<KLEEmneplanKomponent> responseRootEntity = restTemplate.getForEntity("https://www.klxml.dk/download/XML-ver2-0/KLE-Emneplan_Version2-0.xml", KLEEmneplanKomponent.class);

		KLEEmneplanKomponent kleKLEEmneplan = responseRootEntity.getBody();

		for (HovedgruppeKomponent hg : kleKLEEmneplan.getHovedgruppe()) {
			Kle kle = new Kle();
			kle.setCode(hg.getHovedgruppeNr());
			kle.setName(hg.getHovedgruppeTitel());
			kle.setActive(true);
			kle.setParent("0");
			
			updatedKleList.add(kle);
			
			for (GruppeKomponent group : hg.getGruppe()) {
				Kle groupKle = new Kle();
				groupKle.setCode(group.getGruppeNr());
				groupKle.setName(group.getGruppeTitel());
				groupKle.setActive(true);			
				groupKle.setParent(group.getGruppeNr().substring(0, 2));

				updatedKleList.add(groupKle);
				
				for (EmneKomponent subject : group.getEmne()) {
					Kle emneKle = new Kle();
					emneKle.setCode(subject.getEmneNr());
					emneKle.setName(subject.getEmneTitel());
					emneKle.setActive(true);
					emneKle.setParent(subject.getEmneNr().substring(0, 5));

					updatedKleList.add(emneKle);
				}
			}
		}

		return updatedKleList;
	}
	
	public static String getName(String code) {
		return kleCacheMap.get(code);
	}
}
