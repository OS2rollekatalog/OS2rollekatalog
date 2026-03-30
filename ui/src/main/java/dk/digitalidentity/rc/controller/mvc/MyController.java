package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Slf4j
@Controller
public class MyController {
	private final AssignmentService assignmentService;
	private final UserService userService;

	@GetMapping("/ui/my")
	public String my(Model model) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("principal: " + SecurityUtil.getUserId() + " does not exist");
			return "redirect:/";
		}

		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(user);
		Set<CurrentAssignment> onePrRoleGroupAssignment = assignmentService.getUniqueRoleGroupAssignments(assignments);

		List<RoleAssignedToUserDTO> allAssignmentsAsDTO = new ArrayList<>();
		allAssignmentsAsDTO.addAll(assignments.stream().map(a -> RoleAssignedToUserDTO.fromCurrentAssignmentUserRole(a, assignmentService.getAssignedThrough(a))).toList());
		allAssignmentsAsDTO.addAll(onePrRoleGroupAssignment.stream().map(a -> RoleAssignedToUserDTO.fromCurrentAssignmentRoleGroup(a, assignmentService.getAssignedThroughForRoleGroup(a))).toList());

		model.addAttribute("user", user);
		model.addAttribute("assignments", allAssignmentsAsDTO);

		return "users/my";
	}

}
