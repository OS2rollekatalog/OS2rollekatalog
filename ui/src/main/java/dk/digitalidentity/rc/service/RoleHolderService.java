package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.RoleWeightService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleHolderDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleHolderService {
	private final AssignmentService assignmentService;
	private final RoleWeightService roleWeightService;
	private final UserRoleService userRoleService;
	private final PositionService positionService;
	private final MessageSource messageSource;

	/**
	 * The users holding the role, limited to those the reader may see. Only direct assignments can be
	 * edited, and only when the role is assignable under the assign constraint.
	 */
	@Transactional(readOnly = true)
	public List<RoleHolderDTO> findHolders(UserRole userRole, PermissionConstraint readConstraint, PermissionConstraint assignConstraint) {
		Set<CurrentAssignment> assignments = assignmentService.getActiveByUserRoleWithDetails(userRole);
		Set<String> outweighedHolderUuids = roleWeightService.findOutweighedHolderUuids(userRole);
		boolean roleAssignable = userRoleService.isUserRoleAssignable(userRole, true, assignConstraint);

		List<Holder> holders = assignments.stream()
			.map(assignment -> {
				AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
				return new Holder(assignment.getUser(), assignedThrough, RoleAssignedToUserDTO.fromCurrentAssignmentUserRole(assignment, assignedThrough));
			})
			.filter(holder -> readConstraint.allowsOrgunit(holder.assignment().getOrgUnitUuid()))
			.toList();

		Map<String, List<Position>> positionsByUserUuid = positionService.getWithOrgUnitByUserUuid(
			holders.stream().map(Holder::user).collect(Collectors.toSet()));
		Texts texts = new Texts(messageSource, LocaleContextHolder.getLocale());

		return holders.stream()
			.map(holder -> toDto(holder,
				texts,
				outweighedHolderUuids.contains(holder.user().getUuid()),
				roleAssignable && holder.assignedThrough() == AssignedThrough.DIRECT,
				positionsByUserUuid.getOrDefault(holder.user().getUuid(), List.of())))
			.toList();
	}

	private static RoleHolderDTO toDto(Holder holder, Texts texts, boolean outweighed, boolean canEdit, List<Position> positions) {
		User user = holder.user();
		RoleAssignedToUserDTO assignment = holder.assignment();

		return new RoleHolderDTO(
			user.getName(),
			user.isDisabled() ? texts.disabled() : null,
			outweighed ? texts.outweighed() : null,
			user.getUserId(),
			texts.assignedThrough(holder.assignedThrough()),
			texts.validity(assignment.getStartDate(), assignment.getStopDate()),
			positions.stream().map(texts::position).toList(),
			new RoleHolderDTO.Edit(
				assignment.getAssignmentId(),
				user.getUuid(),
				assignment.getName(),
				assignment.getOrgUnitUuid(),
				assignment.getCaseNumber(),
				assignment.getStartDate(),
				assignment.getStopDate(),
				canEdit));
	}

	private record Holder(User user, AssignedThrough assignedThrough, RoleAssignedToUserDTO assignment) {
	}

	/** The texts of the users tab, each resolved once per request */
	private static final class Texts {
		private final MessageSource messageSource;
		private final Locale locale;
		private final String disabled;
		private final String outweighed;
		private final String assigned;
		private final String deassigned;
		private final String indefinite;
		private final String in;
		private final Map<AssignedThrough, String> assignedThrough = new EnumMap<>(AssignedThrough.class);

		Texts(MessageSource messageSource, Locale locale) {
			this.messageSource = messageSource;
			this.locale = locale;
			this.disabled = message("html.entity.user.disabled");
			this.outweighed = message("html.page.users.manage.assignment.ineffective") + " " + message("html.page.users.manage.assignment.ineffective.systemRoleWeight");
			this.assigned = message("html.word.assigned");
			this.deassigned = message("html.word.deassigned");
			this.indefinite = message("html.word.indefinite");
			this.in = message("html.word.in");
		}

		String disabled() {
			return disabled;
		}

		String outweighed() {
			return outweighed;
		}

		String assignedThrough(AssignedThrough value) {
			return assignedThrough.computeIfAbsent(value, v -> message(v.getMessage()));
		}

		List<String> validity(LocalDate startDate, LocalDate stopDate) {
			if (startDate == null && stopDate == null) {
				return List.of(indefinite);
			}

			List<String> lines = new ArrayList<>(2);
			if (startDate != null) {
				lines.add(assigned + " " + startDate);
			}
			if (stopDate != null) {
				lines.add(deassigned + " " + stopDate);
			}
			return lines;
		}

		String position(Position position) {
			return position.getName() + " " + in + " " + position.getOrgUnit().getName();
		}

		private String message(String key) {
			return messageSource.getMessage(key, null, locale);
		}
	}
}
