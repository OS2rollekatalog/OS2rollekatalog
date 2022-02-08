package dk.digitalidentity.rc.service.os2sync;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.service.os2sync.model.KleDto;
import dk.digitalidentity.rc.service.os2sync.model.KleDtoWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OS2syncKlassifikationService {

	@Qualifier("defaultRestTemplate")
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	public Collection<Kle> load(String cvr) {
		Map<String, Kle> kleMap = new HashMap<>();

		HttpEntity<KleDtoWrapper> responseRootEntity = restTemplate.getForEntity(configuration.getIntegrations().getKle().getOs2SyncUrl() + "/odata/Klasser?$filter=KlasseTilhoerer eq '00000c7e-face-4001-8000-000000000000' and Cvr eq '" + cvr + "'&$select=UUID,BrugervendtNoegle,Titel,Tilstand", KleDtoWrapper.class);

		for (KleDto kleDto : responseRootEntity.getBody().getContent()) {
			Kle kle = new Kle();
			kle.setCode(kleDto.getCode());
			kle.setName(kleDto.getTitle());
			kle.setActive(kleDto.isActive());

			if (kleDto.getCode().length() == 2) {
				kle.setParent("0");
			}
			else if (kleDto.getCode().length() == 5) {
				kle.setParent(kleDto.getCode().substring(0, 2));
			}
			else if (kleDto.getCode().length() == 8) {
				kle.setParent(kleDto.getCode().substring(0, 5));
			} 
			else {
				log.warn("Invalid KLE: " + kleDto.getCode());
				continue;
			}

			if (kleMap.containsKey(kle.getCode())) {
				Kle otherKle = kleMap.get(kle.getCode());
				if (otherKle.isActive() == kle.isActive()) {
					log.warn("KLE with code " + kle.getCode() + " is in the set from KOMBIT twice with same status");
				}
				else if (!otherKle.isActive()) {
					// overwrite with active version (KOMBIT bug, they are keeping multple versions instead of updating the actual version)
					kleMap.put(kle.getCode(), kle);
				}
			}
			else {
				kleMap.put(kle.getCode(), kle);
			}
		}

		return kleMap.values();
	}
}
