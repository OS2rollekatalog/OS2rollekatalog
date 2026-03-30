package dk.digitalidentity.rc.controller.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RequireControllerPermission(section = Section.MANAGER, permission = Permission.READ)
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("rest/managerdelegate")
public class ManagerDelegateRestController {
	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final ManagerDelegateService managerDelegateService;

	public record Select2ItemDTO(String id, String text){}
	public record Select2DTO(List<Select2ItemDTO> results) {}

	@GetMapping("managers")
	public ResponseEntity<?> searchManager(@RequestParam(required = false) String search) {

		boolean isAdmin = SecurityUtil.hasDirectAdminRole();
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		List<User> users = new ArrayList<>();

		if(isAdmin) {
			//If admin, show every manager
			users = orgUnitService.getManagers();

		} else {
			//otherwise user is manager, show only managers for related orgunits
			users = userService.getManager(loggedInUser);

		}

		if(search != null && !search.isEmpty()) {
			List<User> filteredUsers = users.stream().filter(m ->
					m.getName().toLowerCase().contains(search.toLowerCase())
							|| m.getUserId().toLowerCase().contains(search.toLowerCase())).toList();
			users = filteredUsers;
		}

		return ResponseEntity.ok( new Select2DTO(users.stream().map(u -> new Select2ItemDTO(u.getUuid(), u.getName()+" ("+u.getUserId()+")")).toList()));
	}

	@GetMapping("users")
	public ResponseEntity<?> searchUsers(@RequestParam(required = false) String search) {

		List<User> foundManagers = new ArrayList<>();

		if(search != null && !search.isEmpty()) {
			foundManagers = userService.searchUsers(search);
		} else  {
			foundManagers = userService.findTop10();
		}

		return ResponseEntity.ok( new Select2DTO(foundManagers.stream().map(u -> new Select2ItemDTO(u.getUuid(), u.getName()+" ("+u.getUserId()+")")).toList()));
	}

	public record CreateManagerDelegateDTO (String managerUuid, String delegateUuid, @JsonFormat(pattern = "dd-MM-yyyy") LocalDate fromDate, @Nullable @JsonFormat(pattern = "dd-MM-yyyy") LocalDate toDate, boolean indefinitely) {}
	@RequirePermission(section = Section.MANAGER, permission = Permission.READ)
	@PostMapping("create")
	public ResponseEntity<?> createManagerDelegate(@RequestBody CreateManagerDelegateDTO createDTO) {

		managerDelegateService.upsert(null, createDTO.managerUuid, createDTO.delegateUuid, createDTO.fromDate, createDTO.toDate, createDTO.indefinitely);

		return ResponseEntity.ok().build();
	}

	@RequirePermission(section = Section.MANAGER, permission = Permission.DELETE)
	@DeleteMapping("delete/{id}")
	public ResponseEntity<?> deleteManagerDelegate(@PathVariable long id) {

		managerDelegateService.delete(id);

		return ResponseEntity.ok().build();
	}

	public record UpdateManagerDelegateDTO(Long id, String managerUuid, String delegateUuid, @JsonFormat(pattern = "dd-MM-yyyy") LocalDate fromDate, @JsonFormat(pattern = "dd-MM-yyyy") LocalDate toDate, boolean indefinitely) {}
	@RequirePermission(section = Section.MANAGER, permission = Permission.UPDATE)
	@PostMapping("update")
	public ResponseEntity<?> updateManagerDelegate(@RequestBody UpdateManagerDelegateDTO updateDTO) {

		managerDelegateService.upsert(updateDTO.id, updateDTO.managerUuid, updateDTO.delegateUuid, updateDTO.fromDate, updateDTO.toDate, updateDTO.indefinitely);

		return ResponseEntity.ok().build();
	}

}
