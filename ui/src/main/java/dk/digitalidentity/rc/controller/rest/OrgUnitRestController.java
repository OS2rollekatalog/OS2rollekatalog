package dk.digitalidentity.rc.controller.rest;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
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
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
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
	private SettingsService settingsService;

	@Autowired
	private AuditLogger auditLogger;

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
			case "PERFORMING":
			case "KlePrimary":
				kleType = KleType.PERFORMING;
				break;
			case "INTEREST":
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
	public ResponseEntity<?> addRole(@PathVariable("uuid") String uuid,
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
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		orgUnitService.addUserRole(ou, role, inherit, startDate, stopDate, (payload != null ? payload.getExceptedUserUuids() : null),(payload != null ? payload.getTitleUuids() : null));
		orgUnitService.save(ou);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/editrole/{uuid}/{assignmentId}")
	public ResponseEntity<OUAssignStatus> editUserRoleAssignment(@PathVariable("uuid") String uuid,
			@PathVariable("assignmentId") long assignmentId,
			@RequestParam(name = "inherit", required = false, defaultValue = "false") boolean inherit,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr,
			@RequestBody StringArrayWrapper payload) {

		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
				
				if (startDate.equals(LocalDate.now())) {
					startDate = null;
				}
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}

		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		OrgUnitUserRoleAssignment assignment = ou.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignmentId).findAny().orElse(null);
		if (assignment == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (assignment.getUserRole().getItSystem().getSystemType() == ItSystemType.AD && assignment.getUserRole().getItSystem().isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (orgUnitService.updateUserRoleAssignment(ou, assignment, inherit, startDate, stopDate, (payload != null ? payload.getExceptedUserUuids() : null), (payload != null ? payload.getTitleUuids(): null))) {
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/{uuid}/removeassignment/{type}/{assignmentId}")
	public ResponseEntity<String> removeUserRoleOrRoleGroupAssignmentFromOrgUnit(@PathVariable("uuid") String uuid, @PathVariable("assignmentId") long assignmentId, @PathVariable("type") RoleAssignmentType type) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (type == RoleAssignmentType.USERROLE) {
			if (orgUnitService.removeUserRoleAssignment(ou, assignmentId)) {
				orgUnitService.save(ou);
			}
		} else if (type == RoleAssignmentType.ROLEGROUP) {
			if (orgUnitService.removeRoleGroupAssignment(ou, assignmentId)) {
				orgUnitService.save(ou);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
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
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		orgUnitService.addRoleGroup(ou, roleGroup, inherit, startDate, stopDate, (payload != null ? payload.getExceptedUserUuids() : null),(payload != null ? payload.getTitleUuids() : null));
		orgUnitService.save(ou);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/editrolegroup/{uuid}/{assignmentId}")
	public ResponseEntity<OUAssignStatus> editRoleGroupAssignment(@PathVariable("uuid") String uuid,
			@PathVariable("assignmentId") long assignmentId,
			@RequestParam(name = "inherit", required = false, defaultValue = "false") boolean inherit,
			@RequestParam(name = "startDate", required = false) String startDateStr,
			@RequestParam(name = "stopDate", required = false) String stopDateStr,
			@RequestBody StringArrayWrapper payload) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);

		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		LocalDate startDate = null, stopDate = null;
		if (StringUtils.hasLength(startDateStr)) {
			try {
				startDate = LocalDate.parse(startDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid startdate string: " + startDateStr);
			}
		}
		if (StringUtils.hasLength(stopDateStr)) {
			try {
				stopDate = LocalDate.parse(stopDateStr);
			}
			catch (Exception ex) {
				log.warn("Invalid stopdate string: " + stopDateStr);
			}
		}

		OrgUnitRoleGroupAssignment assignment = ou.getRoleGroupAssignments().stream().filter(rga -> rga.getId() == assignmentId).findAny().orElse(null);
		if(assignment == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (orgUnitService.updateRoleGroupAssignment(ou, assignment, inherit, startDate, stopDate, (payload != null ? payload.getExceptedUserUuids() : null), (payload != null ? payload.getTitleUuids(): null))) {
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
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
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@GetMapping("/rest/ous/{uuid}/role/{assignmentId}/titles")
	public ResponseEntity<List<String>> getRoleOus(@PathVariable("uuid") String uuid, @PathVariable("assignmentId") long roleAssignmentId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		List<OrgUnitUserRoleAssignment> titlesAssignedToOuWithUserRole = ou.getUserRoleAssignments().stream()
				.filter(ura -> ura.getId() == roleAssignmentId)
				.collect(Collectors.toList());
		
		List<String> titleUuids = titlesAssignedToOuWithUserRole.stream()
				.flatMap(ura -> ura.getTitles().stream())
				.map(Title::getUuid).collect(Collectors.toList());

		return new ResponseEntity<>(titleUuids, HttpStatus.OK);
	}
	
    @GetMapping(value = "/rest/ous/titles/{uuid}")
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
					.map(title -> new TitleListForm(title, false))
					.collect(Collectors.toList());
			
			List<String> titleFormsUuids = titleForms.stream().map(t -> t.getId()).collect(Collectors.toList());
			titleForms.addAll(ou.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).collect(Collectors.toList()));
			
			return new ResponseEntity<>(titleForms, HttpStatus.OK);
		}
		else {
			return new ResponseEntity<>(null, HttpStatus.OK);
		}
    }
	
	@RequireAssignerRole
	@GetMapping("/rest/ous/{uuid}/rolegroup/{assignmentId}/titles")
	public ResponseEntity<List<String>> getRoleGroupOus(@PathVariable("uuid") String uuid, @PathVariable("assignmentId") long assignmentId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		List<OrgUnitRoleGroupAssignment> titlesAssignedToOuWithRoleGroup = ou.getRoleGroupAssignments().stream()
				.filter(rga -> rga.getId() == assignmentId)
				.collect(Collectors.toList());
		
		List<String> titleUuids = titlesAssignedToOuWithRoleGroup.stream()
				.flatMap(ura -> ura.getTitles().stream())
				.map(Title::getUuid).collect(Collectors.toList());

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

}
