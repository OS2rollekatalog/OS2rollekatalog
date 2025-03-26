package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserroleDatatableDao;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableITSystemDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleDTO;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserRoleService {

	@Autowired
	private UserRoleDao userRoleDao;

	@Autowired
	private UserroleDatatableDao userroleDatatableDao;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private AccessConstraintService assignerRoleConstraint;

	@Autowired
	private UserService userService;
    @Autowired
    private AccessConstraintService accessConstraintService;

	public Optional<SystemRoleAssignmentConstraintValue> findConstraintValue(final ConstraintType constraintType, final SystemRoleAssignment assignment) {
		return assignment.getConstraintValues().stream()
				.filter(c -> c.getConstraintType().getUuid().equals(constraintType.getUuid()))
				.findFirst();
	}

	@AuditLogIntercepted
	public void addSystemRoleConstraint(final SystemRoleAssignment assignment, final SystemRoleAssignmentConstraintValue constraintValue) {
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

	public List<UserRole> getByItSystem(ItSystem itSystem) {
		return userRoleDao.findByItSystem(itSystem);
	}

	@AuditLogIntercepted
	public UserRole save(UserRole userRole) {
		return userRoleDao.save(userRole);
	}

	@AuditLogIntercepted
	public void delete(UserRole userRole) {
		userRoleDao.delete(userRole);
	}

	// TODO: auditlog? This is not exposed in UI, so....
	public void deleteAll(List<UserRole> userRoles) {
		userRoleDao.deleteAll(userRoles);
	}

	public List<UserRole> getAll() {
		return userRoleDao.findAll();
	}

	public List<UserRole> getAllRequestable() {
		return userRoleDao.findByCanRequestTrue();
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

	public boolean canRequestRole(UserRole role, User user) {
		if (!role.isCanRequest()) {
			return false;
		}
		return true;
	}

	// TODO: bør man ikke filtere på hvilke systemer som brugeren faktisk kan anmode om?
	public List<UserRole> whichRolesCanBeRequestedByUser(List<UserRole> roles, User user) {

		// filter on canRequest
		roles = roles.stream().filter(r -> r.isCanRequest()).collect(Collectors.toList());

		return roles;
	}

	@Transactional
	public void updateLinkedUserRoles() {
		List<UserRole> userRoles = findByLinkedSystemRoleNotNull();

		for (UserRole userRole : userRoles) {
			boolean changes = false;

			SystemRole linkedSystemRole = userRole.getLinkedSystemRole();
			if (linkedSystemRole == null) {
				log.warn("Linked systemrole does not exist any more for userRole: " + userRole.getId());
				userRole.setLinkedSystemRole(null);
				userRole.setLinkedSystemRolePrefix(null);
				changes = true;
			} else {
				String prefix = userRole.getLinkedSystemRolePrefix();
				if (prefix == null) {
					prefix = "";
				}

				String newName = prefix + linkedSystemRole.getName();
				if (newName.length() > 64) {
					newName = newName.substring(0, 64);
				}

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


	public DataTablesOutput<UserRoleDTO> getAvailableAsDatatable(DataTablesInput input, User user) {
		final List<Long> itSystemsUserCanEdit = accessConstraintService.itSystemsUserCanEdit();
		DataTablesOutput<UserRole> UserroleOutput = userroleDatatableDao.findAll(input, (Specification<UserRole>) (root, query, criteriaBuilder) -> {
			final List<Predicate> andPredicates = new ArrayList<>();
			if (itSystemsUserCanEdit != null) {
				andPredicates.add(root.get("itSystem").get("id").in(itSystemsUserCanEdit));
			}
			if (!SecurityUtil.isAdmin()) {
				//filter out RC internal roles
				andPredicates.add(root.get("itSystem").get("identifier").in(Constants.ROLE_CATALOGUE_IDENTIFIER).not());
			}
            return criteriaBuilder.and(andPredicates.toArray(new Predicate[0]));
        });
		List<UserRole> userRoles = UserroleOutput.getData();
		List<RoleAssignedToUserDTO> assignments = userService.getAllUserRoleAndRoleGroupAssignments(user);

		DataTablesOutput<UserRoleDTO> output = new DataTablesOutput<>();
		output.setDraw(UserroleOutput.getDraw());
		output.setError(UserroleOutput.getError());
		output.setRecordsTotal(UserroleOutput.getRecordsTotal());
		output.setRecordsFiltered(UserroleOutput.getRecordsFiltered());
		output.setData(userRoles.stream().map(ur -> new UserRoleDTO(
				ur.getId(),
				ur.getName(),
				ur.getDescription(),
				new AvailableITSystemDTO(ur.getItSystem().getName(), ur.getItSystem().getSystemType().toString()),
				assignments.stream().filter(a -> a.getType() == RoleAssignmentType.USERROLE).anyMatch(a -> a.getRoleId() == ur.getId()))
		).toList());
		return output;
	}

	public DataTablesOutput<UserRole> getAllItsystemNotDeletedNotPostponedAsDatatable(DataTablesInput input) {
		return userroleDatatableDao.findAll(input, Specification
				.where(UserroleDatatableDao.itSystemNotDeleted()
						.and(UserroleDatatableDao.notAllowPostponing())));
	}
}
