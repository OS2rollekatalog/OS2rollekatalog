package dk.digitalidentity.rc.controller.rest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.viewmodel.KleViewModel;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUAssignStatus;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireKleAdministratorRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@RequireReadAccessRole
@RestController
public class OrgUnitRestController {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private KleService kleService;

	@Autowired
	private UserService userService;

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
	public ResponseEntity<String> addRole(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId, @RequestParam(name = "inherit", required = false, defaultValue = "false") boolean inherit) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (ou == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		// make sure noone tries to trick us ;)
		inherit = role.isOuInheritAllowed() && inherit;

		if (orgUnitService.addUserRole(ou, role, inherit)) {
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAssignerRole
	@GetMapping(value = "/rest/ous/assignedStatus/{objType}/{uuid}/{roleId}")
	public ResponseEntity<OUAssignStatus> alreadyAssigned(@PathVariable("objType") String objType, @PathVariable("uuid") String uuid, @PathVariable("roleId") long roleId) {
		if (objType == null || (!objType.equals("role") && !objType.equals("rolegroup"))) {
			log.warn("unknown objType: " + objType);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);			
		}

		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("orgUnit does not exist: " + uuid);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		int amount = 0;
		if (objType.equals("role")) {
			UserRole userRole = userRoleService.getById(roleId);
			if (userRole == null) {
				log.warn("userRole does not exist: " + roleId);
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);			
			}

			List<User> usersByOrgUnit = userService.findByOrgUnit(orgUnit);
			for (User user : usersByOrgUnit) {
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
		}
		else {
			RoleGroup roleGroup = roleGroupService.getById(roleId);
			if (roleGroup == null) {
				log.warn("roleGroup does not exist: " + roleId);
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);				
			}
			
			List<User> usersByOrgUnit = userService.findByOrgUnit(orgUnit);
			for (User user : usersByOrgUnit) {
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
		}

		OUAssignStatus status = new OUAssignStatus();
		status.setSuccess(amount == 0);
		status.setUsers(amount);

		return ResponseEntity.ok(status);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/removerole/{uuid}/{roleid}")
	public ResponseEntity<String> removeRoleAsync(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (ou == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (orgUnitService.removeUserRole(ou, role)) {
			orgUnitService.save(ou);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/ous/addrolegroup/{uuid}/{rolegroupid}")
	public ResponseEntity<String> addRolegroup(@PathVariable("uuid") String uuid, @PathVariable("rolegroupid") long roleroupId, @RequestParam(name = "inherit", required = false, defaultValue = "false") boolean inherit) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleroupId);

		if (ou == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		// make sure noone tries to trick us ;)
		inherit = roleGroup.isOuInheritAllowed() && inherit;
		
		if (orgUnitService.addRoleGroup(ou, roleGroup, inherit)) {
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

		if (orgUnitService.removeRoleGroup(ou, roleGroup)) {
			orgUnitService.save(ou);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping(value = "/rest/ous/getKle/{parentCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<KleViewModel> getKle(@PathVariable("parentCode") String parentCode){
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
}
