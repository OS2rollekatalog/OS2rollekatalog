package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.KleDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.model.ItSystemSelect2DTO;
import dk.digitalidentity.rc.service.model.KleSelect2DTO;
import dk.digitalidentity.rc.service.model.OrgUnitSelect2DTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@EnableCaching
@EnableScheduling
public class Select2Service {

	@Autowired
	private KleDao kleDao;

	@Autowired
	private OrgUnitDao orgUnitDao;

	@Autowired
	private ItSystemService itSystemService;

	@Cacheable(value = "kleList")
	public List<KleSelect2DTO> getKleList() {
		List<Kle> kleList = kleDao.findAll();
		List<KleSelect2DTO> kleSelect2DTOList = new ArrayList<>();

		if (kleList != null) {
			for (Kle kle : kleList) {
				KleSelect2DTO kleSelect2DTO = new KleSelect2DTO();

				String kleCode = kle.getCode();
				String kleName = kleCode + " " + kle.getName();
				if (kleCode != null && kleCode.length() < 6) {
					kleCode = kleCode + ".*";
				}

				kleSelect2DTO.setId(kleCode);
				kleSelect2DTO.setText(kleName);

				kleSelect2DTOList.add(kleSelect2DTO);
			}
		}

		return kleSelect2DTOList;
	}

	@Cacheable(value = "orgunitList")
	public List<OrgUnitSelect2DTO> getOrgUnitList() {
		List<OrgUnit> orgUnitList = orgUnitDao.findByActiveTrue(); // TODO: consider using service instead, so we can use Specifications
		List<OrgUnitSelect2DTO> orgUnitSelect2DTOList = new ArrayList<>();

		if (orgUnitList != null) {
			for (OrgUnit orgUnit : orgUnitList) {
				OrgUnitSelect2DTO orgUnitSelect2DTO = new OrgUnitSelect2DTO();

				orgUnitSelect2DTO.setId(orgUnit.getUuid());
				orgUnitSelect2DTO.setText(orgUnit.getName());

				orgUnitSelect2DTOList.add(orgUnitSelect2DTO);
			}
		}

		return orgUnitSelect2DTOList;
	}

	@Cacheable(value = "itSystemList")
	public List<ItSystemSelect2DTO> getItSystemList() {
		List<ItSystem> itSystems = itSystemService.getAll();
		List<ItSystemSelect2DTO> itSystemSelect2DTOList = new ArrayList<>();

		if (itSystems != null) {
			for (ItSystem itSystem : itSystems) {
				ItSystemSelect2DTO itSystemSelect2DTO = new ItSystemSelect2DTO();

				itSystemSelect2DTO.setId(itSystem.getId());
				itSystemSelect2DTO.setText(itSystem.getName());

				itSystemSelect2DTOList.add(itSystemSelect2DTO);
			}
		}

		return itSystemSelect2DTOList;
	}

	// 24 hours, as we never update
	@Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
	@CacheEvict(value = "kleList", allEntries = true)
	public void resetKleCache() {
		;
	}

	// 1 hour cache, should be fine for a single user session
	@Scheduled(fixedDelay = 4 * 60 * 60 * 1000)
	@CacheEvict(value = "orgunitList", allEntries = true)
	public void resetOUCache() {
		;
	}

	// 15 minute cache is enough, small dataset after all
	@Scheduled(fixedDelay = 15 * 60 * 1000)
	@CacheEvict(value = "itSystemList", allEntries = true)
	public void resetITSystemCache() {
		;
	}
}
