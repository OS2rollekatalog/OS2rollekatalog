package dk.digitalidentity.rc.controller.rest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;

@RequireReadAccessRole
@RestController
public class TitleRestController {

	@Autowired
	private TitleService titleService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@RequireAssignerRole
	@GetMapping("/rest/titles/{uuid}/role/{roleid}/ous")
	public ResponseEntity<List<String>> getRoleOus(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId) {
		Title title = titleService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (title == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		for (TitleUserRoleAssignment assignment : title.getUserRoleAssignments()) {
			if (assignment.getUserRole().getId() == roleId) {
				return new ResponseEntity<List<String>>(assignment.getOuUuids(), HttpStatus.OK);
			}
		}

		return new ResponseEntity<>(new ArrayList<String>(), HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/titles/addrole/{uuid}/{roleid}")
	public ResponseEntity<String> addRole(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId, @RequestBody String[] ouUuids) {
		Title title = titleService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (title == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (titleService.addUserRole(title, role, ouUuids)) {
			titleService.save(title);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/titles/removerole/{uuid}/{roleid}")
	public ResponseEntity<String> removeRoleAsync(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId) {
		Title title = titleService.getByUuid(uuid);
		UserRole role = userRoleService.getById(roleId);

		if (title == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (titleService.removeUserRole(title, role)) {
			titleService.save(title);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@GetMapping("/rest/titles/{uuid}/rolegroup/{roleid}/ous")
	public ResponseEntity<List<String>> getRoleGroupOus(@PathVariable("uuid") String uuid, @PathVariable("roleid") long roleId) {
		Title title = titleService.getByUuid(uuid);
		RoleGroup role = roleGroupService.getById(roleId);

		if (title == null || role == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		for (TitleRoleGroupAssignment assignment : title.getRoleGroupAssignments()) {
			if (assignment.getRoleGroup().getId() == roleId) {
				return new ResponseEntity<List<String>>(assignment.getOuUuids(), HttpStatus.OK);
			}
		}

		return new ResponseEntity<>(new ArrayList<String>(), HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping(value = "/rest/titles/addrolegroup/{uuid}/{rolegroupid}")
	public ResponseEntity<String> addRolegroup(@PathVariable("uuid") String uuid, @PathVariable("rolegroupid") long roleroupId, @RequestBody String[] ouUuids) {
		Title title = titleService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleroupId);

		if (title == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (titleService.addRoleGroup(title, roleGroup, ouUuids)) {
			titleService.save(title);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAssignerRole
	@PostMapping(value = "/rest/titles/removerolegroup/{uuid}/{rolegroupid}")
	public ResponseEntity<String> removeRoleGroup(@PathVariable("uuid") String uuid, @PathVariable("rolegroupid") long roleGroupId) {
		Title title = titleService.getByUuid(uuid);
		RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

		if (title == null || roleGroup == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (titleService.removeRoleGroup(title, roleGroup)) {
			titleService.save(title);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
