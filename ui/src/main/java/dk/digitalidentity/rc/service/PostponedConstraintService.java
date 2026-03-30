package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentConstraintValueDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.dao.PostponedConstraintDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeValueSet;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class PostponedConstraintService {
    private final PostponedConstraintDao postPonedConstraintDao;
    private final OrgUnitService orgUnitService;
    private final ItSystemService itSystemService;
    private final ConstraintTypeService constraintTypeService;

    public PostponedConstraint save(PostponedConstraint postponedConstraint) {
        return postPonedConstraintDao.save(postponedConstraint);
    }

    public boolean isValidConstraint(ConstraintType constraintType, String value, long systemRoleId) {
        // Check that it has a value
        if (!StringUtils.hasLength(value)) {
            log.warn("Value is null or empty for system role id  " + systemRoleId + " and constraint type " + constraintType.getUuid());
            return false;
        }

        // perform regex validation (if needed)
        if (constraintType.getUiType().equals(ConstraintUIType.REGEX) && constraintType.getRegex() != null && constraintType.getRegex().length() > 0) {
            try {
                Pattern pattern = Pattern.compile(constraintType.getRegex());
                Matcher matcher = pattern.matcher(value);
                if (!matcher.matches()) {
                    log.warn("Input does not match regular expression: " + value + " for regex: " + constraintType.getRegex());
                    return false;
                }
            } catch (Exception ex) {
                log.warn("Unable to perform regex validation (giving it a free pass) on '" + constraintType.getEntityId() + "'. Message = " + ex.getMessage());
            }
        }
        return true;
    }

    public List<PostponedConstraint> getPostPonedConstraintValues(User user, SystemRoleAssignment systemRoleAssignment) {
        return postPonedConstraintDao.findByUserUserRoleAssignment_User_UuidAndSystemRole_IdAndUserUserRoleAssignment_UserRole_Id(user.getUuid(), systemRoleAssignment.getSystemRole().getId(), systemRoleAssignment.getUserRole().getId());
    }

    public Set<PostponedConstraint> findAllForUserAndRoleCatalogue(User user) {
        return postPonedConstraintDao.findByUserUserRoleAssignment_UserAndConstraintType_EntityIdIn(user, Set.of(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID));
    }

    public Set<PostponedConstraint> findAllForSystemRoleAndUserRole(String systemRoleIdentifier, UserRole userRole) {
        return postPonedConstraintDao.findBySystemRole_IdentifierAndUserUserRoleAssignment_UserRole(systemRoleIdentifier, userRole);
    }

    /**
     * Resolves raw postponed constraint data into human-readable display values for a given user role.
     * Iterates over the role's system role assignments, finds any postponed constraint values,
     * and resolves them to display strings (e.g. OU UUIDs to OU names, IT system IDs to IT system names).
     */
    public List<SystemRoleAssignmentDTO> resolvePostponedConstraintDisplayValues(UserRole userRole, Set<CurrentAssignmentPostponedConstraint> postponedConstraints) {
		List<SystemRoleAssignmentDTO> result = new ArrayList<>();
		if (postponedConstraints == null || !userRole.isAllowPostponing()) {
			return result;
		}

        for (SystemRoleAssignment systemRoleAssignment : userRole.getSystemRoleAssignments()) {
            List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

            for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
                if (!constraintValue.isPostponed()) {
                    continue;
                }

                SystemRoleAssignmentConstraintValueDTO valueDto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);
                CurrentAssignmentPostponedConstraint postponedConstraint = postponedConstraints.stream()
                    .filter(p -> p.getSystemRoleId() == systemRoleAssignment.getSystemRole().getId()
                        && p.getConstraintTypeUuid().equals(constraintValue.getConstraintType().getUuid()))
                    .findAny()
                    .orElse(null);

                if (postponedConstraint != null) {
                    valueDto.setConstraintValue(resolveConstraintDisplayValue(postponedConstraint));
                }

                postponedConstraintValues.add(valueDto);
            }

            if (!postponedConstraintValues.isEmpty()) {
                SystemRoleAssignmentDTO dto = new SystemRoleAssignmentDTO();
                dto.setSystemRole(systemRoleAssignment.getSystemRole());
                dto.setPostponedConstraints(postponedConstraintValues);
                result.add(dto);
            }
        }

        return result;
    }

    /**
     * Dispatches to the appropriate display value resolver based on the constraint's UI type (REGEX, COMBO_SINGLE, or COMBO_MULTI).
     */
    private String resolveConstraintDisplayValue(CurrentAssignmentPostponedConstraint postponedConstraint) {
		return switch (postponedConstraint.getConstraintTypeUIType()) {
			case ConstraintUIType.REGEX -> resolveRegexConstraintDisplayValue(postponedConstraint);
			case ConstraintUIType.COMBO_SINGLE -> resolveComboSingleDisplayValue(postponedConstraint);
			case ConstraintUIType.COMBO_MULTI -> resolveComboMultiDisplayValue(postponedConstraint);
		};
	}

    /**
     * Resolves REGEX-type constraints by looking up entity names.
     * For OU constraints, resolves UUIDs to OrgUnit names. For IT system constraints, resolves IDs to ItSystem names.
     * Falls back to the raw comma-joined values for other entity types.
     */
    private String resolveRegexConstraintDisplayValue(CurrentAssignmentPostponedConstraint postponedConstraint) {
        String entityId = postponedConstraint.getConstraintTypeEntityId();

        if (entityId.equals(Constants.OU_CONSTRAINT_ENTITY_ID) || entityId.equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)) {
			StringBuilder ouString = getOuString(postponedConstraint);
			return ouString.toString();
        } else if (entityId.equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
			StringBuilder itSystemsString = getItSystemsString(postponedConstraint);
            return itSystemsString.toString();
        }

        return String.join(",", postponedConstraint.getValue());
    }

	/** Resolves a list of OU UUIDs to a comma-separated string of OrgUnit names. */
	private StringBuilder getOuString(CurrentAssignmentPostponedConstraint postponedConstraint) {
		StringBuilder ouString = new StringBuilder();
		for (String ouUuid : postponedConstraint.getValue()) {
			OrgUnit ou = orgUnitService.getByUuid(ouUuid);
			if (ou != null) {
				if (!ouString.isEmpty()) {
					ouString.append(", ");
				}
				ouString.append(ou.getName());
			}
		}
		return ouString;
	}

	/** Resolves a list of IT system IDs to a comma-separated string of ItSystem names. */
	private StringBuilder getItSystemsString(CurrentAssignmentPostponedConstraint postponedConstraint) {
		StringBuilder itSystemsString = new StringBuilder();
		for (String id : postponedConstraint.getValue()) {
			ItSystem itSystem = itSystemService.getById(Integer.parseInt(id));
			if (itSystem != null) {
				if (!itSystemsString.isEmpty()) {
					itSystemsString.append(", ");
				}
				itSystemsString.append(itSystem.getName());
			}
		}
		return itSystemsString;
	}

	/** Resolves a COMBO_SINGLE constraint by looking up the display value from the constraint type's value set. */
	private String resolveComboSingleDisplayValue(CurrentAssignmentPostponedConstraint postponedConstraint) {
        ConstraintType constraintType = constraintTypeService.getByUuid(postponedConstraint.getConstraintTypeUuid());
        if (constraintType != null) {
            ConstraintTypeValueSet valueSet = constraintType.getValueSet().stream()
                .filter(v -> v.getConstraintKey().equals(postponedConstraint.getValue().stream().findFirst().orElse(null)))
                .findAny()
                .orElse(null);
            return valueSet == null ? "" : valueSet.getConstraintValue();
        }
        return "";
    }

    /** Resolves a COMBO_MULTI constraint by looking up multiple display values from the constraint type's value set and joining them. */
    private String resolveComboMultiDisplayValue(CurrentAssignmentPostponedConstraint postponedConstraint) {
        ConstraintType constraintType = constraintTypeService.getByUuid(postponedConstraint.getConstraintTypeUuid());
        if (constraintType != null) {
            List<ConstraintTypeValueSet> valueSets = constraintType.getValueSet().stream()
                .filter(v -> postponedConstraint.getValue().contains(v.getConstraintKey()))
                .toList();
            StringBuilder valuesString = new StringBuilder();
            for (ConstraintTypeValueSet valueSet : valueSets) {
                if (!valuesString.isEmpty()) {
                    valuesString.append(", ");
                }
                valuesString.append(valueSet.getConstraintValue());
            }
            return valuesString.toString();
        }
        return "";
    }

}
