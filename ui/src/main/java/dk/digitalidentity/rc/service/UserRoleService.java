package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.controller.rest.model.UserRoleViewDTO;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleDTO;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserRoleService {
	private final UserRoleDao userRoleDao;
	private final UserRoleViewDatatableDao userRoleViewDatatableDao;
	private final ItSystemService itSystemService;
	private final UserService userService;
	private final UserRoleViewDao userRoleViewDao;
	private final UserPermissionContext userPermissionContext;

	/**
	 * Determines if a role assignment is ineffective due to being overridden by a role with higher weight.
	 * This method evaluates whether an assignment becomes ineffective when there are other assignments
	 * with higher system role weights that would take precedence.
	 *
	 * @param assignment the role assignment to evaluate for effectiveness
	 * @param assignments list of all assignments to compare against
	 * @return true if the assignment is ineffective due to higher weight roles, false otherwise
	 */
	public boolean isIneffectiveDueToWeight(final RoleAssignedToUserDTO assignment, final List<RoleAssignedToUserDTO> assignments) {
		if (assignment == null) {
			return false;
		}
		return userRoleDao.findById(assignment.getRoleId())
			.map(ur -> {
				int thisRoleWeight = highestSystemRolesWeight(ur);
				return assignments.stream()
					.filter(a -> a.getAssignmentId() != assignment.getAssignmentId()) // Don't compare assignment to itself
					.filter(a -> a.getType() != RoleAssignmentType.NEGATIVE && a.getType() != RoleAssignmentType.NEGATIVE_ROLEGROUP)
					.filter(a -> a.getType() == RoleAssignmentType.USERROLE)
					.filter(a -> (a.getStartDate() == null || !a.getStartDate().isAfter(LocalDate.now()))
									&& (a.getStopDate() == null || !a.getStopDate().isBefore(LocalDate.now())))
					.filter(a -> a.getItSystem() != null
						&& ur.getItSystem() != null && a.getItSystem().getId() == ur.getItSystem().getId())
					.mapToInt(a -> highestSystemRolesWeight(getById(a.getRoleId())))
					.anyMatch(w -> w > thisRoleWeight);
			})
			.orElse(false);
	}

	/**
	 * Calculates the highest weight among all system roles assigned to a user role.
	 * Returns 1 as the default weight if no system roles are assigned or if no weights are found.
	 *
	 * @param userRole the user role to analyze for highest system role weight
	 * @return the highest weight value among assigned system roles, or 1 if none found
	 */
	public int highestSystemRolesWeight(final UserRole userRole) {
		if (userRole == null || userRole.getSystemRoleAssignments() == null) {
			return 0;
		}
		return userRole.getSystemRoleAssignments().stream()
			.map(SystemRoleAssignment::getSystemRole)
			.filter(Objects::nonNull)
			.map(SystemRole::getWeight)
			.mapToInt(Integer::intValue)
			.max()
			.orElse(1);
	}

	public Optional<SystemRoleAssignmentConstraintValue> findConstraintValue(final ConstraintType constraintType, final SystemRoleAssignment assignment) {
		return assignment.getConstraintValues().stream()
				.filter(c -> c.getConstraintType().getUuid().equals(constraintType.getUuid()))
				.findFirst();
	}

	@AuditLogIntercepted
	public void addSystemRoleConstraint(final SystemRoleAssignment assignment, final SystemRoleAssignmentConstraintValue constraintValue) {
		if (assignment.getConstraintValues() == null) {
			assignment.setConstraintValues(new ArrayList<>());
		}
		assignment.getConstraintValues().add(constraintValue);
	}

	@AuditLogIntercepted
	public void updateSystemRoleConstraint(final SystemRoleAssignment assignment, final SystemRoleAssignmentConstraintValue constraintValue) {
		final SystemRoleAssignmentConstraintValue foundConstraint = findConstraintValue(constraintValue.getConstraintType(), assignment)
				.orElseThrow(IllegalArgumentException::new);
		foundConstraint.setConstraintValue(constraintValue.getConstraintValue());
		foundConstraint.setSystemRoleAssignment(constraintValue.getSystemRoleAssignment());
		foundConstraint.setConstraintType(constraintValue.getConstraintType());
		foundConstraint.setConstraintValueType(constraintValue.getConstraintValueType());
		foundConstraint.setPostponed(constraintValue.isPostponed());
		foundConstraint.setConstraintIdentifier(constraintValue.getConstraintIdentifier());
		assignment.getConstraintValues().add(foundConstraint);
	}

	@AuditLogIntercepted
	public void removeSystemRoleConstraint(final SystemRoleAssignment assignment, final ConstraintType type) {
		findConstraintValue(type, assignment)
				.ifPresent(c -> assignment.getConstraintValues().remove(c));
	}

	@AuditLogIntercepted
	public boolean addSystemRoleAssignment(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (!userRole.getSystemRoleAssignments().contains(systemRoleAssignment)) {
			userRole.getSystemRoleAssignments().add(systemRoleAssignment);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeSystemRoleAssignment(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (userRole.getSystemRoleAssignments().contains(systemRoleAssignment)) {
			userRole.getSystemRoleAssignments().remove(systemRoleAssignment);

			return true;
		}

		return false;
	}

	public List<UserRole> getAllSensitiveRoles() {
		return userRoleDao.findBySensitiveRoleTrue();
	}

	public List<UserRole> getByItSystemId(long itSystemId) {
		return userRoleDao.findByItSystem(itSystemService.getById(itSystemId));
	}

	@Transactional
	public List<UserRole> getByItSystem(ItSystem itSystem) {
		List<UserRole> userRoles = userRoleDao.findByItSystem(itSystem);

		userRoles.forEach(ur -> ur.getSystemRoleAssignments().size());

		return userRoles;
	}

	@Transactional
	@AuditLogIntercepted
	public UserRole save(UserRole userRole) {
		return userRoleDao.save(userRole);
	}

	@Transactional
	@AuditLogIntercepted
	public void delete(UserRole userRole) {
		userRoleDao.delete(userRole);
	}

	// not exposed in UI or API, so not auditlogged
	@Transactional
	public void deleteAll(List<UserRole> userRoles) {
		userRoleDao.deleteAll(userRoles);
	}

	// not exposed in UI or API, so not auditlogged
	@Transactional
	public void saveAll(List<UserRole> userRoles) {
		userRoleDao.saveAll(userRoles);
	}

	public List<UserRole> getAll() {
		return userRoleDao.findAll();
	}

	public List<UserRole> getUserRolesWithRequesterPermissions(List<RequestableBy> permissions) {
		return userRoleDao.findByRequesterPermissionIn(permissions);
	}

	public List<UserRole> getUserRolesWithInheritedPermissionsMatching(List<RequestableBy> permissions) {
		//Treat null values as INHERIT values
		if (permissions.contains(RequestableBy.INHERIT)) {
			return userRoleDao.findByRequesterPermissionAndItSystem_RequesterPermissionInOrItSystem_RequesterPermissionNull(RequestableBy.INHERIT, permissions);
		}
		return userRoleDao.findByRequesterPermissionAndItSystem_RequesterPermissionIn(RequestableBy.INHERIT, permissions);
	}

	public List<UserRole> getUserRolesWithApproverPermissions( List<ApprovableBy> permissions) {
		return userRoleDao.findByApproverPermissionIn(permissions);
	}

	public UserRole getById(long roleId) {
		return userRoleDao.findById(roleId).orElse(null);
	}

	public Optional<UserRole> getOptionalById(long roleId) {
		return userRoleDao.findById(roleId);
	}

	public UserRole getByNameAndItSystem(String name, ItSystem itSystem) {
		return userRoleDao.getByNameAndItSystem(name, itSystem);
	}

	public UserRole getByIdentifier(String identifier) {
		return userRoleDao.getByIdentifier(identifier);
	}

	@Transactional
	public void updateLinkedUserRoles() {
		List<UserRole> userRoles = findByLinkedSystemRoleNotNull();

		for (UserRole userRole : userRoles) {
			boolean changes = false;

			SystemRole linkedSystemRole = userRole.getLinkedSystemRole();
			String prefix = userRole.getLinkedSystemRolePrefix();
			if (prefix == null) {
				prefix = "";
			}

			String newName = prefix + linkedSystemRole.getName();
			if (newName.length() > 64) {
				newName = newName.substring(0, 64);
			}

			switch (userRole.getSystemRoleLinkType()) {
				case NONE:
					// Will not happen as we findByLinkedSystemRoleNotNull
					break;
				case NAME_ONLY: {
					// compare name
					if (!Objects.equals(userRole.getName(), newName)) {
						userRole.setName(newName);
						changes = true;
					}
					break;
				}
				case NAME_AND_DESCRIPTION:
					// compare name
					if (!Objects.equals(userRole.getName(), newName)) {
						userRole.setName(newName);
						changes = true;
					}

					// compare description
					if (!Objects.equals(userRole.getDescription(), linkedSystemRole.getDescription())) {
						userRole.setDescription(linkedSystemRole.getDescription());
						changes = true;
					}
					break;
				default:
					log.error("Unexpected SystemRoleLinkType: " + userRole.getSystemRoleLinkType());
			}

			if (changes) {
				save(userRole);
			}
		}
	}

	public int countBySystemRoleAssignmentsSystemRole(SystemRole systemRole) {
		return userRoleDao.countBySystemRoleAssignmentsSystemRole(systemRole);
	}

	public List<UserRole> findByLinkedSystemRoleNotNull() {
		return userRoleDao.findByLinkedSystemRoleNotNull();
	}

	public UserRole getByItSystemAndIdentifier(ItSystem itSystem, String identifier) {
		return userRoleDao.getByItSystemAndIdentifier(itSystem, identifier);
	}

	public List<UserRole> getByItSystemAndDelegatedFromCvrNotNull(ItSystem itSystem) {
		return userRoleDao.getByItSystemAndDelegatedFromCvrNotNull(itSystem);
	}

	public List<UserRole> getByDelegatedFromCvrNotNullAndItSystemIdentifierNot(String itSystemIdentifier) {
		return userRoleDao.getByDelegatedFromCvrNotNullAndItSystemIdentifierNot(itSystemIdentifier);
	}

	public List<String> getOUFilterUuidsWithChildren(UserRole userRole) {
		Set<String> selectedOUs = new HashSet<>();
		for (OrgUnit ou : userRole.getOrgUnitFilterOrgUnits()) {
			if (!selectedOUs.contains(ou.getUuid())) {
				addChildrenRecursive(ou, selectedOUs);
			}
		}

		return new ArrayList<>(selectedOUs);
	}

	private void addChildrenRecursive(OrgUnit ou, Set<String> selectedOUs) {
		selectedOUs.add(ou.getUuid());

		if (ou.getChildren() == null || ou.getChildren().isEmpty()) {
			return;
		}

		for (OrgUnit child : ou.getChildren()) {
			addChildrenRecursive(child, selectedOUs);
		}
	}

	public DataTablesOutput<UserRoleDTO> getAvailableAsDatatable(DataTablesInput input, User user) {
		PermissionConstraint permissionConstraint = userPermissionContext.getConstraints(Section.USER_ROLE, Permission.READ);

		final Set<Long> readableItSystems = permissionConstraint.getConstrainedItSystemIds();
		DataTablesOutput<UserRoleView> userroleOutput = userRoleViewDatatableDao.findAll(input, (Specification<UserRoleView>) (root, query, criteriaBuilder) -> {
			final List<Predicate> andPredicates = new ArrayList<>();
			andPredicates.add(criteriaBuilder.isFalse(root.get("readOnly")));
			if (readableItSystems == null) {
				return criteriaBuilder.conjunction(); // allowed all
			}
			if (readableItSystems.isEmpty()) {
				return criteriaBuilder.disjunction(); // allowed none
			}
			andPredicates.add(root.get("itSystemId").in(readableItSystems));
			return criteriaBuilder.and(andPredicates.toArray(new Predicate[0]));
		});
		List<UserRoleView> userRoles = userroleOutput.getData();
		List<RoleAssignedToUserDTO> assignments = userService.getAllUserRoleAndRoleGroupAssignments(user);

		DataTablesOutput<UserRoleDTO> output = new DataTablesOutput<>();
		output.setDraw(userroleOutput.getDraw());
		output.setError(userroleOutput.getError());
		output.setRecordsTotal(userroleOutput.getRecordsTotal());
		output.setRecordsFiltered(userroleOutput.getRecordsFiltered());
		output.setData(userRoles.stream().map(ur -> new UserRoleDTO(
				ur.getId(),
				ur.getName(),
				ur.getDescription(),
				ur.getItSystemName(),
				ur.getItSystemType().getMessage(),
				assignments.stream().filter(a -> a.getType() == RoleAssignmentType.USERROLE).anyMatch(a -> a.getRoleId() == ur.getId()))
		).toList());
		return output;
	}

	public UserRole createForSystemRoles(String name, String description, ItSystem itSystem, String identifier, List<SystemRole> systemRoles, String assignedBy, String assignedByUserId) {
		UserRole userRole = new UserRole();
		userRole.setName(name);
		userRole.setDescription(description);
		userRole.setIdentifier(identifier);
		userRole.setItSystem(itSystem);
		userRoleDao.save(userRole);

		List<SystemRoleAssignment> systemRoleAssignments = new ArrayList<>();
		for (SystemRole systemRole : systemRoles) {
			systemRoleAssignments.add(createSystemRoleAssignment(systemRole, userRole, assignedBy, assignedByUserId));
		}

		userRole.setSystemRoleAssignments(systemRoleAssignments);
		return userRoleDao.save(userRole);
	}

	public SystemRoleAssignment createSystemRoleAssignment(SystemRole systemRole, UserRole userRole, String assignedBy, String assignedByUserId) {
		SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
		systemRoleAssignment.setSystemRole(systemRole);
		systemRoleAssignment.setUserRole(userRole);
		systemRoleAssignment.setAssignedByName(assignedBy);
		systemRoleAssignment.setAssignedByUserId(assignedByUserId);
		systemRoleAssignment.setAssignedTimestamp(new Date());

		if (userRole.getSystemRoleAssignments() == null) {
			userRole.setSystemRoleAssignments(new ArrayList<>());
		}
		userRole.getSystemRoleAssignments().add(systemRoleAssignment);
		return systemRoleAssignment;
	}

	/**
	 * Specification for UserRoleView checking if role's it system is in an allowed set
	 * @param itSystemIds allowed it system ids
	 * @return specification
	 */
	public static Specification<UserRoleView> hasItSystemIdIn(Set<Long> itSystemIds) {
		return (root, query, cb) -> {
			if (itSystemIds == null) {
				return cb.conjunction(); // all is allowed
			}
			if ( itSystemIds.isEmpty()) {
				return null; // none is allowed
			}
			return root.get("itSystemId").in(itSystemIds);
		};
	}

	public DataTablesOutput<UserRoleViewDTO> getAllAsConstraintFilteredAsDatatable(DataTablesInput input) {
		Set<Long> constrainedITSystems = userPermissionContext.getConstraint(Section.USER_ROLE, Permission.READ).getConstrainedItSystemIds();
		DataTablesOutput<UserRoleView> viewOutput = userRoleViewDao.findAll(input, Specification.where(hasItSystemIdIn(constrainedITSystems)));

		List<UserRoleViewDTO> dtoList = viewOutput.getData().stream()
				// Map to DTO
				.map(view -> {
					ItemPermissionDTO specificAllowedActions = getSpecificAllowedActionsForUserRoleDTO(view);
					UserRoleViewDTO dto = new UserRoleViewDTO(view);
					dto.setAllowedActions(specificAllowedActions);
					return dto;
				})
				.toList();

		DataTablesOutput<UserRoleViewDTO> dtoOutput = new DataTablesOutput<>();
		dtoOutput.setDraw(viewOutput.getDraw());
		dtoOutput.setRecordsTotal(viewOutput.getRecordsTotal());
		dtoOutput.setRecordsFiltered(viewOutput.getRecordsFiltered());
		dtoOutput.setData(dtoList);
		dtoOutput.setError(viewOutput.getError());
		return dtoOutput;
	}

	public ItemPermissionDTO getSpecificAllowedActionsForUserRoleDTO(final UserRoleView view) {
		Long itSystemId = view.getItSystemId();
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.USER_ROLE, Permission.READ);
		PermissionConstraint updateConstraint =  userPermissionContext.getConstraint(Section.USER_ROLE, Permission.UPDATE);
		PermissionConstraint deleteConstraint =  userPermissionContext.getConstraint(Section.USER_ROLE, Permission.DELETE);
		PermissionConstraint createConstraint = userPermissionContext.getConstraint(Section.USER_ROLE, Permission.CREATE);

		boolean canRead = readConstraint== null || readConstraint.allowsITSystem(itSystemId)
				&& view.getDelegatedFromCvr() == null;
		boolean canCreate =createConstraint == null || createConstraint.allowsITSystem(itSystemId)
				&& !view.isReadOnly();
		boolean canUpdate = updateConstraint == null || updateConstraint.allowsITSystem(itSystemId)
				&& !view.isReadOnly();
		boolean canDelete = deleteConstraint == null || deleteConstraint.allowsITSystem(itSystemId)
				&& !view.isReadOnly()
				&& view.getItSystemType() != ItSystemType.KSPCICS;

		return new ItemPermissionDTO(
				canCreate,
				canRead,
				canUpdate,
				canDelete
		);
	}

	public Set<UserRole> findAllBySystemRole(SystemRole systemRole) {
		return userRoleDao.findBySystemRoleAssignments_SystemRole(systemRole);
	}

}
