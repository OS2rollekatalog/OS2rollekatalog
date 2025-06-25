package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ManagerSubstituteDao;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;

@Service
public class ManagerSubstituteService {
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private ManagerSubstituteDao managerSubstituteDao;

	public ManagerSubstitute findById(long id) {
		return managerSubstituteDao.findById(id).orElseThrow(() -> new NotFoundException("ManagerSubstitute not found"));
	}

	public boolean isSubstituteforOrgUnit(OrgUnit orgUnit) {
		User user = getCurrentlyLoggedInUser();
		if (user != null) {
			return isSubstituteforOrgUnit(user, orgUnit);
		}
		
		return false;
	}

	public boolean isSubstituteforOrgUnit(User user, OrgUnit orgUnit) {
		return getSubstitutesForOrgUnit(orgUnit).stream().anyMatch(sm -> Objects.equals(sm.getUuid(), user.getUuid()));
	}

	public boolean isManagerForOrgUnit(OrgUnit orgUnit) {
		User user = getCurrentlyLoggedInUser();
		if (user != null) {
			return isManagerForOrgUnit(user, orgUnit);
		}
		
		return false;
	}
	
	public boolean isManagerForOrgUnit(User user, OrgUnit orgUnit) {
		if (orgUnit.getManager() == null) {
			return false;
		}
		
		return (Objects.equals(user.getUuid(), orgUnit.getManager().getUuid()));
	}
	
	public List<User> getSubstitutesForOrgUnit(OrgUnit ou) {
		if (ou.getManager() == null ) {
			return new ArrayList<>();
		}

		return ou.getManager().getManagerSubstitutes().stream()
				.filter(ms -> Objects.equals(ms.getOrgUnit().getUuid(), ou.getUuid()))
				.map(ManagerSubstitute::getSubstitute)
				.collect(Collectors.toList());
	}

	public boolean hasSubstitute(OrgUnit orgUnit) {
		return (orgUnit.getManager() != null && orgUnit.getManager().getManagerSubstitutes().size() > 0);
	}
	
	private User getCurrentlyLoggedInUser() {
		String userId = SecurityUtil.getUserId();
		
		if (userId != null) {
			return userService.getByUserId(userId);
		}
		
		return null;
	}

	public void deleteById(long id) {
		managerSubstituteDao.deleteById(id);
	}

	public List<ManagerSubstitute> getAll() {
		return managerSubstituteDao.findAll();
	}
}
