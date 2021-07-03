package dk.digitalidentity.rc.controller.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUAssignStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
import dk.digitalidentity.rc.controller.rest.model.StringArrayWrapper;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireKleAdministratorRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.WhoCanRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireReadAccessRole
@RestController
public class OrgUnitRestController {

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private KleService kleService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private TitleService titleService;
	
	@Autowired
	private PositionService positionService;
	
	@Autowired
	private SettingsService settingsService;

	@Autowired
	private AuditLogger auditLogger;

	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Map<Object, Boolean> seen = new ConcurrentHashMap<>();
		return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}

	@GetMapping(value = "/rest/ous/getKle/{parentCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<KleViewModel> getKle(@PathVariable("parentCode") String parentCode) {
		return kleService.getKleListFromParent(parentCode);
	}
	
	@RequireKleAdministratorRole
	@PostMapping(value = "/rest/ous/{uuid}/inherit")
	@ResponseBody
	public HttpEntity<String> setKleInherit(@PathVariable("uuid") String uuid, @RequestParam(name = "active") boolean active) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (active) {
			ou.setInheritKle(true);
			orgUnitService.save(ou);
		}
		else {
			ou.setInheritKle(false);
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/ous/updateAll/kle")
	@ResponseBody
	public HttpEntity<String> updateKle(@RequestHeader("uuid") String uuid, @RequestHeader("type") String type, @RequestBody List<String> codes) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		KleType kleType;
		switch (type) {
			case "KlePrimary":
				kleType = KleType.PERFORMING;
				break;
			case "KleSecondary":
				kleType = KleType.INTEREST;
				break;
			default:
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		Set<String> oldCodes = ou.getKles().stream().filter(kleMapping -> kleMapping.getAssignmentType().equals(kleType)).map(KLEMapping::getCode).collect(Collectors.toSet());
		Set<String> newCodes = new HashSet<>(codes);

		//Find difference
		Set<String> intersect = new HashSet<>(oldCodes);
		intersect.retainAll(newCodes);

		oldCodes.removeAll(intersect);
		newCodes.removeAll(intersect);

		//Add / Remove differences one by one
		for (String code : oldCodes) {
			orgUnitService.removeKLE(ou, kleType, code);
		}

		for (String code : newCodes) {
			orgUnitService.addKLE(ou, kleType, code);
		}
		orgUnitService.save(ou);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAdministratorRole
	@PostMapping(value = "/rest/ous/{uuid}/setLevel/{level}")
	public ResponseEntity<String> setLevel(@PathVariable("uuid") String uuid, @PathVariable("level") OrgUnitLevel level) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>("Ukendt enhed", HttpStatus.BAD_REQUEST);
		}

		List<OrgUnitLevel> allowedLevels = orgUnitService.getAllowedLevels(ou);
		
		if (!allowedLevels.contains(level)) {
			return new ResponseEntity<>("Tildeling ikke lovlig", HttpStatus.FORBIDDEN);
		}

		ou.setLevel(level);
		orgUnitService.save(ou);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/addrole/{uuid}/{roleid}")
	public ResponseEntity<OUAssignStatus> addRole(@PathVariable("uuid") String uuid,
			@PathVariable("roleid") long roleId,
			@RequestParam(name = "inherit", required = false, defaultValue = "false") boolean inherit,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr,
			@RequestBody StringArrayWrapper payload) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (ou == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (role.getItSystem().getSystemType() == ItSystemType.AD && role.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		OUAssignStatus status = alreadyAssigned(ou, role, (payload != null) ? payload.getTitleUuids() : null);

		if (!configuration.getTitles().isEnabled() || payload == null || payload.getTitleUuids() == null || payload.getTitleUuids().size() == 0) {

			if (orgUnitService.addUserRole(ou, role, inherit, startDate, stopDate, (payload != null ? payload.getExceptedUserUuids() : null))) {
				orgUnitService.save(ou);
				status.setSuccess(true);
			}
			
			// if already assigned through titles, we should remove the title assignment as the new assignment overwrites
			removeTitleAssignedUserRole(ou, role);
		}
		else {
			status.setSuccess(true);

			List<Title> titles = positionService.findByOrgUnit(ou)
					.stream()
					.filter(p -> p.getTitle() != null)
					.map(p -> p.getTitle())
					.filter(distinctByKey(t -> t.getUuid()))
					.collect(Collectors.toList());

			for (Title title : titles) {
				boolean found = false;
				
				for (String titleUuid : payload.getTitleUuids()) {
					if (titleUuid.equals(title.getUuid())) {
						found = true;
						break;
					}
				}

				List<String> ouUuids = new ArrayList<>();
				for (TitleUserRoleAssignment assignment : title.getUserRoleAssignments()) {
					if (assignment.getUserRole().getId() == roleId) {
						ouUuids = assignment.getOuUuids();
						break;
					}
				}

				if (found) {		
					// already there, do nothing
					if (ouUuids.contains(ou.getUuid())) {
						continue;
					}
					
					ouUuids.add(ou.getUuid());
					if (titleService.addUserRole(title, role, ouUuids.toArray(new String[0]), startDate, stopDate)) {
						titleService.save(title);
					}
				}
				else {
					// if not there, do nothing
					if (!ouUuids.contains(ou.getUuid())) {
						continue;
					}

					if (titleService.removeUserRole(title, role, ou)) {
						titleService.save(title);
					}
				}
			}
			
			// assigning by titles should remove the direct assignment (if present)
			if (ou.getUserRoleAssignments().stream().anyMatch(a -> a.getUserRole().getId() == role.getId())) {
				if (orgUnitService.removeUserRole(ou, role)) {
					orgUnitService.save(ou);
				}
			}
		}

		return new ResponseEntity<>(status, HttpStatus.OK);
	}
	

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/removerole/{uuid}/{roleid}")
	public ResponseEntity<String> removeRoleAsync(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (ou == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (role.getItSystem().getSystemType() == ItSystemType.AD && role.getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (ou.getUserRoleAssignments().stream().anyMatch(a -> a.getUserRole().getId() == role.getId())) {
			if (orgUnitService.removeUserRole(ou, role)) {
				orgUnitService.save(ou);
			}
		}

		removeTitleAssignedUserRole(ou, role);

		return new ResponseEntity<>(HttpStatus.OK);
	}	

	@RequireAssignerRole
	@PostMapping("/rest/ous/addrolegroup/{uuid}/{rolegroupid}")
	public ResponseEntity<OUAssignStatus> addRolegroup(@PathVariable("uuid") String uuid,
			@PathVariable("rolegroupid") long roleGroupId,
			@RequestParam(name = "inherit", required = false, defaultValue = "false") boolean inherit,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr,
			@RequestBody StringArrayWrapper payload) {
		
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

		if (ou == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		LocalDate startDate = null, stopDate = null;
		if (!StringUtils.isEmpty(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (!StringUtils.isEmpty(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		OUAssignStatus status = alreadyAssigned(ou, roleGroup, (payload != null) ? payload.getTitleUuids() : null);

		// if no titles supplied (or enabled), do direct assignment
		if (!configuration.getTitles().isEnabled() || payload == null || payload.getTitleUuids() == null || payload.getTitleUuids().size() == 0) {
			if (orgUnitService.addRoleGroup(ou, roleGroup, inherit, startDate, stopDate, (payload != null ? payload.getExceptedUserUuids() : null))) {
				orgUnitService.save(ou);
				status.setSuccess(true);
			}
			
			// if already assigned through titles, we should remove the title assignment as the new assignment overwrites
			removeTitleAssignedRoleGroup(ou, roleGroup);
		}
		else {
			List<Title> titles = positionService.findByOrgUnit(ou)
					.stream()
					.filter(p -> p.getTitle() != null)
					.map(p -> p.getTitle())
					.filter(distinctByKey(t -> t.getUuid()))
					.collect(Collectors.toList());

			for (Title title : titles) {
				boolean found = false;
				
				for (String titleUuid : payload.getTitleUuids()) {
					if (titleUuid.equals(title.getUuid())) {
						found = true;
						break;
					}
				}

				List<String> ouUuids = new ArrayList<>();
				for (TitleRoleGroupAssignment assignment : title.getRoleGroupAssignments()) {
					if (assignment.getRoleGroup().getId() == roleGroupId) {
						ouUuids = assignment.getOuUuids();
						break;
					}
				}

				if (found) {		
					// already there, do nothing
					if (ouUuids.contains(ou.getUuid())) {
						continue;
					}
					
					ouUuids.add(ou.getUuid());
					if (titleService.addRoleGroup(title, roleGroup, ouUuids.toArray(new String[0]), startDate, stopDate)) {
						titleService.save(title);
					}
				}
				else {
					// if not there, do nothing
					if (!ouUuids.contains(ou.getUuid())) {
						continue;
					}

					if (titleService.removeRoleGroup(title, roleGroup, ou)) {
						titleService.save(title);
					}
				}
			}
			
			// directly assigned rolegroup is removed as the title assignment overwrites this
			if (ou.getRoleGroupAssignments().stream().anyMatch(a -> a.getRoleGroup().getId() == roleGroup.getId())) {
				if (orgUnitService.removeRoleGroup(ou, roleGroup)) {
					orgUnitService.save(ou);
				}
			}
		}

		return new ResponseEntity<>(status, HttpStatus.OK);
	}
	
	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/removerolegroup/{uuid}/{rolegroupid}")
	public ResponseEntity<String> removeRoleGroup(@PathVariable("uuid") String uuid, @PathVariable("rolegroupid") long roleGroupId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

		if (ou == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (ou.getRoleGroupAssignments().stream().anyMatch(a -> a.getRoleGroup().getId() == roleGroup.getId())) {
			if (orgUnitService.removeRoleGroup(ou, roleGroup)) {
				orgUnitService.save(ou);
			}
		}
		
		removeTitleAssignedRoleGroup(ou, roleGroup);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private void removeTitleAssignedRoleGroup(OrgUnit ou, RoleGroup roleGroup) {
		if (configuration.getTitles().isEnabled()) {
			List<Title> titles = positionService.findByOrgUnit(ou)
					.stream()
					.filter(p -> p.getTitle() != null)
					.map(p -> p.getTitle())
					.filter(distinctByKey(t -> t.getUuid()))
					.collect(Collectors.toList());

			for (Title title : titles) {
				List<String> ouUuids = new ArrayList<>();
				for (TitleRoleGroupAssignment assignment : title.getRoleGroupAssignments()) {
					if (assignment.getRoleGroup().getId() == roleGroup.getId()) {
						ouUuids = assignment.getOuUuids();
						break;
					}
				}
				
				// if not there, do nothing
				if (!ouUuids.contains(ou.getUuid())) {
					continue;
				}
				
				if (titleService.removeRoleGroup(title, roleGroup, ou)) {
					titleService.save(title);
				}
			}
		}
	}

	
	@RequireAssignerRole
	@PostMapping("/rest/ous/{uuid}/role/{roleid}/titles")
	public ResponseEntity<List<String>> getRoleOus(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId, @RequestBody TitleListForm[] titles) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (ou == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		List<String> titleUuids = new ArrayList<String>();

		if (titles != null) {
			for (TitleListForm t : titles) {
				Title title = titleService.getByUuid(t.getId());
				for (TitleUserRoleAssignment assignment : title.getUserRoleAssignments()) {
					if (assignment.getUserRole().getId() == roleId) {
						for (String ouUuid : assignment.getOuUuids()) {
							if (ouUuid.equals(uuid)) {
								titleUuids.add(title.getUuid());
							}
						}
					}
				}
			}
		}
		
		return new ResponseEntity<>(titleUuids, HttpStatus.OK);
	}
	
    @GetMapping(value = "/rest/userroles/titles/{uuid}")
    public ResponseEntity<List<TitleListForm>> getTitlesFromOU(@PathVariable("uuid") String uuid) {
    	OrgUnit ou = orgUnitService.getByUuid(uuid);
    	if (ou == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
    	boolean titlesEnabled = configuration.getTitles().isEnabled();
    	if (titlesEnabled) {
			List<Title> titles = orgUnitService.getTitles(ou);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title))
					.collect(Collectors.toList());
			
			return new ResponseEntity<>(titleForms, HttpStatus.OK);
		}
		else {
			return new ResponseEntity<>(null, HttpStatus.OK);
		}
    }
	
	@RequireAssignerRole
	@PostMapping("/rest/ous/{uuid}/rolegroup/{roleid}/titles")
	public ResponseEntity<List<String>> getRoleGroupOus(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId, @RequestBody TitleListForm[] titles) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleId);

		if (ou == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		List<String> titleUuids = new ArrayList<String>();
		if (titles != null) {
			for (TitleListForm t : titles) {
				Title title = titleService.getByUuid(t.getId());
				for (TitleRoleGroupAssignment assignment : title.getRoleGroupAssignments()) {
					if (assignment.getRoleGroup().getId() == roleId) {
						for (String ouUuid : assignment.getOuUuids()) {
							if (ouUuid.equals(uuid)) {
								titleUuids.add(title.getUuid());
							}
						}
					}
				}
			}
		}
		return new ResponseEntity<>(titleUuids, HttpStatus.OK);
	}
	
	@RequireAdministratorRole
	@PostMapping("/rest/ous/{uuid}/authorizationmanager/save")
	@ResponseBody
	public ResponseEntity<HttpStatus> saveAuthManager(@RequestBody String personUuid, @PathVariable String uuid) {
		if (!settingsService.getRequestApproveWho().equals(WhoCanRequest.AUTHORIZATION_MANAGER)) {
			return ResponseEntity.badRequest().build();
		}
		
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to find orgUnit with uuid = " + uuid);
			return ResponseEntity.badRequest().build();
		}

		User user = userService.getByUuid(personUuid);
		if (user == null) {
			log.warn("Unable to find user with uuid = " + personUuid);
			return ResponseEntity.badRequest().build();
		}

		boolean found = false;
		for (AuthorizationManager authManager : orgUnit.getAuthorizationManagers()) {
			if (Objects.equals(authManager.getUser().getUuid(), user.getUuid())) {
				found = true;
				break;
			}
		}
		
		if (!found) {
			AuthorizationManager manager = new AuthorizationManager();
			manager.setOrgUnit(orgUnit);
			manager.setUser(user);
			orgUnit.getAuthorizationManagers().add(manager);
			orgUnitService.save(orgUnit);
			
			auditLogger.log(orgUnit, EventType.AUTH_MANAGER_ADDED, user);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAdministratorRole
	@PostMapping("/rest/ous/{uuid}/authorizationmanager/remove")
	@ResponseBody
	public ResponseEntity<HttpStatus> removeAuthManager(@RequestBody String personUuid, @PathVariable String uuid) {
		if (!settingsService.getRequestApproveWho().equals(WhoCanRequest.AUTHORIZATION_MANAGER)) {
			return ResponseEntity.badRequest().build();
		}
		
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			return ResponseEntity.badRequest().build();
		}
		
		User user = userService.getByUuid(personUuid);
		if (user == null) {
			return ResponseEntity.badRequest().build();
		}

		for (Iterator<AuthorizationManager> iterator = orgUnit.getAuthorizationManagers().iterator(); iterator.hasNext();) {
			AuthorizationManager authManager = iterator.next();
			
			if (Objects.equals(authManager.getUser().getUuid(), personUuid)) {
				iterator.remove();
				
				auditLogger.log(orgUnit, EventType.AUTH_MANAGER_REMOVED, user);
				break;
			}
		}

		orgUnitService.save(orgUnit);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	private OUAssignStatus alreadyAssigned(OrgUnit orgUnit, UserRole userRole, Set<String> titleUuids) {
		int amount = 0;

		List<User> usersByOrgUnit = userService.findByOrgUnit(orgUnit);
		for (User user : usersByOrgUnit) {
			// if titles available and enabled, skip users that does not match title
			if (configuration.getTitles().isEnabled() && titleUuids != null) {
				boolean found = false;
				
				for (Position position : user.getPositions()) {
					if (position.getTitle() == null) {
						continue;
					}
					
					if (titleUuids.contains(position.getTitle().getUuid())) {
						found = true;
					}
				}
				
				if (!found) {
					continue;
				}
			}

			if (user.getUserRoleAssignments() != null && user.getUserRoleAssignments().size() > 0) {
				List<UserRole> userRoles = user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());
				
				for (UserRole ur : userRoles) {
					if (ur.getId() == userRole.getId()) {
						amount++;
						break;
					}
				}
			}
		}

		OUAssignStatus status = new OUAssignStatus();
		status.setUsers(amount);
		status.setSuccess(false);

		return status;
	}
	
	private OUAssignStatus alreadyAssigned(OrgUnit orgUnit, RoleGroup roleGroup, Set<String> titleUuids) {
		long amount = 0;

		List<User> usersByOrgUnit = userService.findByOrgUnit(orgUnit);

		for (User user : usersByOrgUnit) {

			// if titles available and enabled, skip users that does not match title
			if (configuration.getTitles().isEnabled() && titleUuids != null) {
				boolean found = false;
				
				for (Position position : user.getPositions()) {
					if (position.getTitle() == null) {
						continue;
					}

					if (titleUuids.contains(position.getTitle().getUuid())) {
						found = true;
					}
				}
				
				if (!found) {
					continue;
				}
			}

			if (user.getRoleGroupAssignments() != null && user.getRoleGroupAssignments().size() > 0) {
				List<RoleGroup> roleGroups = user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList());

				for (RoleGroup rg : roleGroups) {
					if (rg.getId() == roleGroup.getId()) {
						amount++;
						break;
					}
				}
			}
		}

		OUAssignStatus status = new OUAssignStatus();
		status.setUsers(amount);
		status.setSuccess(false);

		return status;
	}
	
	private void removeTitleAssignedUserRole(OrgUnit ou, UserRole role) {
		if (configuration.getTitles().isEnabled()) {
			List<Title> titles = positionService.findByOrgUnit(ou)
					.stream()
					.filter(p -> p.getTitle() != null)
					.map(p -> p.getTitle())
					.filter(distinctByKey(t -> t.getUuid()))
					.collect(Collectors.toList());

			for (Title title : titles) {
				List<String> ouUuids = new ArrayList<>();
				for (TitleUserRoleAssignment assignment : title.getUserRoleAssignments()) {
					if (assignment.getUserRole().getId() == role.getId()) {
						ouUuids = assignment.getOuUuids();
						break;
					}
				}
				
				// if not there, do nothing
				if (!ouUuids.contains(ou.getUuid())) {
					continue;
				}
				
				if (titleService.removeUserRole(title, role, ou)) {
					titleService.save(title);
				}
			}
		}
	}
}
