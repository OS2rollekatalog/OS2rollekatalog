package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.KitosITSystem;
import dk.digitalidentity.rc.dao.UserRoleSelect2Dao;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.model.UserRoleSelect2DTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.model.ItSystemSelect2DTO;
import dk.digitalidentity.rc.service.model.OrgUnitSelect2DTO;

@Service
@EnableCaching
@EnableScheduling
public class Select2Service {

	@Autowired
	private OrgUnitDao orgUnitDao;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private KitosITSystemService kitosITSystemService;

	@Autowired
	private Select2Service self;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserRoleSelect2Dao userRoleSelect2Dao;

	// used from html - because multi-valued constraint values are stored as comma-separated strings, and thymeleaf is not so helpful here
	public boolean isSelected(String constraintValue, String id) {
		if (!StringUtils.hasLength(constraintValue)) {
			return false;
		}
		
		String[] constraintValues = constraintValue.split(",");
		for (String cv : constraintValues) {
			if (cv.equals(id)) {
				return true;
			}
		}
		
		return false;
	}
	
	@Cacheable(value = "orgunitList")
	public List<OrgUnitSelect2DTO> getOrgUnitList() {
		List<OrgUnit> orgUnitList = orgUnitDao.findByActiveTrue()
				.stream().filter(o -> orgUnitService.isActiveAndIncluded(o)).collect(Collectors.toList()); // TODO: consider using service instead, so we can use Specifications
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

	@Cacheable(value = "userRoleList")
	public List<UserRoleSelect2DTO>  getUserRoleList() {
		List<UserRole> userRoles = userRoleService.getAll();
		List<UserRoleSelect2DTO> userRoleSelect2DTOList = new ArrayList<>();

		if (userRoles != null) {
			for (UserRole userRole : userRoles) {
				UserRoleSelect2DTO userRoleSelect2DTO = new UserRoleSelect2DTO();

				userRoleSelect2DTO.setId(userRole.getId());
				userRoleSelect2DTO.setText(userRole.getName());
				userRoleSelect2DTO.setItSystemName(userRole.getItSystem().getName());

				userRoleSelect2DTOList.add(userRoleSelect2DTO);
			}
		}

		return userRoleSelect2DTOList;
	}

	@Cacheable(value = "kitosItSystemList")
	public List<ItSystemSelect2DTO>  getKitosITSystemList() {
		List<KitosITSystem> systems = kitosITSystemService.getAll();
		List<ItSystemSelect2DTO> kitosITSystemSelect2DTOList = new ArrayList<>();

		for (KitosITSystem kitosITSystem : systems) {
			ItSystemSelect2DTO kitosITSystemSelect2DTO = new ItSystemSelect2DTO();

			kitosITSystemSelect2DTO.setId(kitosITSystem.getId());
			kitosITSystemSelect2DTO.setText(kitosITSystem.getName());

			kitosITSystemSelect2DTOList.add(kitosITSystemSelect2DTO);
		}

		return kitosITSystemSelect2DTOList;
	}

	public Page<UserRole> findAllSearchableUserroles(PageRequest pageable) {
		return userRoleSelect2Dao.findAll(pageable);
	}

	public Page<UserRole> searchUserroles(String searchTerm, PageRequest pageable) {
		return userRoleSelect2Dao.findByNameContainingIgnoreCase(searchTerm, pageable);
	}

	@Caching(evict = {
		@CacheEvict(value = "orgunitList", allEntries = true),
		@CacheEvict(value = "itSystemList", allEntries = true),
		@CacheEvict(value = "userRoleList", allEntries = true),
		@CacheEvict(value = "kitosItSystemList", allEntries = true)
	})
	public void clearCache() {
		
	}

	// 4 hour cache
	@Scheduled(fixedDelay = 4 * 60 * 60 * 1000)
	public void resetCache() {
		self.clearCache();
	}

}
